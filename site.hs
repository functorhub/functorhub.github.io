--------------------------------------------------------------------------------
{-# LANGUAGE OverloadedStrings #-}
import           Data.Monoid
import           Control.Monad
import           Control.Applicative           (Alternative (..))
import           Control.Exception.Base
import           Hakyll

import qualified Data.Yaml                     as Yaml
import qualified Data.Text                     as T
import qualified Data.Text.Encoding            as T
import qualified Data.Set                      as S
import           Hakyll.Core.Metadata
import           Text.Pandoc
import           Skylighting.Styles
import           Skylighting.Format.HTML

import           Debug.Trace


--------------------------------------------------------------------------------
main :: IO ()
main = hakyll $ do
  create ["index.html"] $ do
    route   idRoute
    compile copyFileCompiler

  match "posts/**" $ do
    route $ setExtension "html"
    compile $
          (pandocCompilerWithTransformM readerOpts writerOpts $
            codeInclude >=> graphvizFilter >=> plantumlFilter >=> htmlFilter)

      >>= saveSnapshot "content"
      >>= loadAndApplyTemplate "templates/post.html"    postCtx
      >>= loadAndApplyTemplate "templates/default.html" postCtx
      >>= relativizeUrls

  create ["assets/all.css"] $ do
    route idRoute
    compile $ do
      frags     <- loadAll "private-assets/css/*.css" >>= (return . fmap itemBody)
      let frags' = frags ++ [styleToCss highlightStyle]
      makeItem $ compressCss $ concat frags'

  match "templates/*" $ compile templateCompiler
  match "private-assets/css/*.css" $ compile getResourceBody

  ["graphviz-images/*", "plantuml-images/*"] `forM_` \f -> match f $ do
    route idRoute
    compile copyFileCompiler

  create ["CNAME"] $ do
    route idRoute
    compile copyFileCompiler

--------------------------------------------------------------------------------
postCtx :: Context String
postCtx = defaultContext

flattenCtx :: Compiler (Context a) -> Context a
flattenCtx cmp = Context $ \k x is -> do
  ctx <- cmp
  (unContext ctx) k x is 


--------------------------------------------------------------------------------
type Script = String

scriptFilter :: Script -> Pandoc -> Compiler Pandoc
scriptFilter scr pdc =
  scriptFilterWith scr readerOpts writerOpts pdc

scriptFilterWith :: Script
                 -> ReaderOptions
                 -> WriterOptions
                 -> Pandoc
                 -> Compiler Pandoc
scriptFilterWith scr readerOpts writerOpts pdc = do
  let inJson = writeJSON writerOpts pdc
  outJson   <- unixFilter scr [] inJson
  let res    = either (error . show) id $ readJSON readerOpts outJson
  return $ res --trace (show res) res


--------------------------------------------------------------------------------
-- Actual filters
pyPandocPlugin :: String -> Pandoc -> Compiler Pandoc
pyPandocPlugin str = scriptFilter $ "./plugins/pandocfilters/examples/" ++ str ++ ".py"

graphvizFilter = pyPandocPlugin "graphviz"
plantumlFilter = pyPandocPlugin "plantuml"
htmlFilter     = pyPandocPlugin "html"
codeInclude    = scriptFilter   "./plugins/pandoc-include-code/dist/build/pandoc-include-code/pandoc-include-code"


--------------------------------------------------------------------------------
highlightStyle = pygments

writerOpts = defaultHakyllWriterOptions {
  writerHighlightStyle  = highlightStyle
, writerHTMLMathMethod  = WebTeX "https://latex.codecogs.com/png.latex?"
, writerTableOfContents = True
, writerTemplate        = Just "$toc$\n$body$"
}
readerOpts = defaultHakyllReaderOptions
