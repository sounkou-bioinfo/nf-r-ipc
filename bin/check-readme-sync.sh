#!/usr/bin/env bash
set -euo pipefail

# README contains executed chunk outputs that can vary by runtime
# (e.g. temporary script paths, run names, timings). Treat this as a
# render smoke test in CI: it must render successfully.
Rscript -e "rmarkdown::render('README.Rmd', output_format='github_document', quiet=TRUE)" >/dev/null
printf 'README.Rmd rendered successfully\n'
