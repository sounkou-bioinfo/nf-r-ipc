echo_external <- function(sample, values, meta) {
  list(
    sample = paste0(sample, "-external"),
    values = values,
    meta = meta
  )
}

explode_external <- function(trigger) {
  stop("boom from external R script")
}
