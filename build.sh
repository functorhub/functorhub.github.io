#!/usr/bin/env bash

git submodule update &&\
  (cd plugins/pandoc-include-code && cabal build)