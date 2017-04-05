#!/usr/bin/env bash

pandoc $1 -s --webtex # -o out.html \
  # --filter pandocfilters/examples/graphviz.py \
  # -c splendor.min.css\
  # --toc

open out.html