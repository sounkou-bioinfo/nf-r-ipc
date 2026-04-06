suppressPackageStartupMessages({
  library(nanoarrow)
})

request_ipc <- Sys.getenv("NFR_REQUEST_IPC", unset = "")
response_ipc <- Sys.getenv("NFR_RESPONSE_IPC", unset = "")
inline_code <- Sys.getenv("NFR_INLINE_CODE", unset = "")

if (request_ipc == "" || response_ipc == "") {
  stop("NFR_REQUEST_IPC and NFR_RESPONSE_IPC must be set")
}

decode_value <- function(nodes, id) {
  row_idx <- which(!is.na(nodes$value_id) & nodes$value_id == id)
  row <- nodes[row_idx, , drop = FALSE]
  if (nrow(row) != 1) {
    stop(sprintf("Invalid value_id: %s", as.character(id)))
  }

  tag <- row$tag[[1]]
  if (tag == "null") return(NULL)
  if (tag == "na_logical") return(NA)
  if (tag == "na_integer") return(NA_integer_)
  if (tag == "na_double") return(NA_real_)
  if (tag == "na_character") return(NA_character_)
  if (tag == "string") return(row$v_string[[1]])
  if (tag == "int64") return(as.numeric(row$v_int64[[1]]))
  if (tag == "float64") return(as.numeric(row$v_float64[[1]]))
  if (tag == "bool") return(isTRUE(row$v_bool[[1]]))

  child_idx <- which(!is.na(nodes$parent_id) & nodes$parent_id == id)
  children <- nodes[child_idx, , drop = FALSE]
  if (tag == "list") {
    if (nrow(children) == 0) return(list())
    children <- children[order(children$index), , drop = FALSE]
    return(lapply(children$value_id, function(cid) decode_value(nodes, cid)))
  }

  if (tag == "map") {
    out <- list()
    if (nrow(children) == 0) return(out)
    for (i in seq_len(nrow(children))) {
      key <- children$key[[i]]
      out[[key]] <- decode_value(nodes, children$value_id[[i]])
    }
    return(out)
  }

  stop(sprintf("Unsupported tag: %s", as.character(tag)))
}

encode_nodes <- function(value, parent_id = NA_real_, key = NA_character_, index = NA_integer_, nodes = list(), next_id = 1L) {
  value_id <- next_id
  next_id <- next_id + 1L

  add_node <- function(tag, v_string = NA_character_, v_int64 = NA_real_, v_float64 = NA_real_, v_bool = NA) {
    nodes[[length(nodes) + 1L]] <<- data.frame(
      value_id = as.numeric(value_id),
      parent_id = as.numeric(parent_id),
      key = as.character(key),
      index = as.integer(index),
      tag = as.character(tag),
      v_string = as.character(v_string),
      v_int64 = as.numeric(v_int64),
      v_float64 = as.numeric(v_float64),
      v_bool = as.logical(v_bool),
      stringsAsFactors = FALSE
    )
  }

  if (is.null(value)) {
    add_node("null")
    return(list(nodes = nodes, next_id = next_id, value_id = value_id))
  }

  if (is.list(value) && is.null(names(value))) {
    add_node("list")
    for (i in seq_along(value)) {
      child <- encode_nodes(value[[i]], parent_id = value_id, key = NA_character_, index = as.integer(i - 1L), nodes = nodes, next_id = next_id)
      nodes <- child$nodes
      next_id <- child$next_id
    }
    return(list(nodes = nodes, next_id = next_id, value_id = value_id))
  }

  if (is.list(value) && !is.null(names(value))) {
    add_node("map")
    for (nm in names(value)) {
      child <- encode_nodes(value[[nm]], parent_id = value_id, key = nm, index = NA_integer_, nodes = nodes, next_id = next_id)
      nodes <- child$nodes
      next_id <- child$next_id
    }
    return(list(nodes = nodes, next_id = next_id, value_id = value_id))
  }

  if (length(value) == 1L && is.logical(value) && is.na(value)) {
    add_node("na_logical")
    return(list(nodes = nodes, next_id = next_id, value_id = value_id))
  }

  if (length(value) == 1L && is.integer(value) && is.na(value)) {
    add_node("na_integer")
    return(list(nodes = nodes, next_id = next_id, value_id = value_id))
  }

  if (length(value) == 1L && is.double(value) && is.na(value)) {
    add_node("na_double")
    return(list(nodes = nodes, next_id = next_id, value_id = value_id))
  }

  if (length(value) == 1L && is.character(value) && is.na(value)) {
    add_node("na_character")
    return(list(nodes = nodes, next_id = next_id, value_id = value_id))
  }

  if (is.character(value) && length(value) == 1L) {
    add_node("string", v_string = value)
    return(list(nodes = nodes, next_id = next_id, value_id = value_id))
  }

  if (is.logical(value) && length(value) == 1L) {
    add_node("bool", v_bool = value)
    return(list(nodes = nodes, next_id = next_id, value_id = value_id))
  }

  if ((is.integer(value) || (is.double(value) && is.finite(value) && floor(value) == value)) && length(value) == 1L) {
    add_node("int64", v_int64 = as.numeric(value))
    return(list(nodes = nodes, next_id = next_id, value_id = value_id))
  }

  if (is.double(value) && length(value) == 1L) {
    add_node("float64", v_float64 = as.numeric(value))
    return(list(nodes = nodes, next_id = next_id, value_id = value_id))
  }

  stop(sprintf("Unsupported output value type: %s", paste(class(value), collapse = "/")))
}

read_request <- function(path) {
  tbl <- as.data.frame(read_nanoarrow(path))
  control_rows <- tbl[tbl$section == "__nfr_control__", , drop = FALSE]
  data_rows <- tbl[tbl$section == "__nfr_data__", , drop = FALSE]
  if (nrow(control_rows) < 1) {
    stop("Request is missing __nfr_control__ row")
  }

  control <- as.list(control_rows[1, , drop = FALSE])
  if (nrow(data_rows) == 0) {
    decoded <- list()
  } else {
    data_rows <- data_rows[!is.na(data_rows$value_id), , drop = FALSE]
    root <- data_rows[is.na(data_rows$parent_id), , drop = FALSE]
    if (nrow(root) != 1) stop("Expected exactly one root value node")
    decoded <- decode_value(data_rows, root$value_id[[1]])
  }

  list(control = control, data = decoded)
}

build_response_frame <- function(control, value) {
  out_nodes <- encode_nodes(value)
  node_df <- do.call(rbind, out_nodes$nodes)
  node_df$section <- "__nfr_data__"

  ctrl_df <- data.frame(
    section = "__nfr_control__",
    protocol_version = as.integer(control$protocol_version %||% 1L),
    call_id = as.character(control$call_id %||% ""),
    script_mode = as.character(control$script_mode %||% "inline"),
    script_ref = as.character(control$script_ref %||% "<inline>"),
    payload_kind = as.character(control$payload_kind %||% "value_graph"),
    status = as.character(control$status %||% "ok"),
    result_kind = as.character(control$result_kind %||% "value_graph"),
    error_class = as.character(control$error_class %||% NA_character_),
    error_message = as.character(control$error_message %||% NA_character_),
    value_id = as.numeric(NA),
    parent_id = as.numeric(NA),
    key = as.character(NA),
    index = as.integer(NA),
    tag = as.character(NA),
    v_string = as.character(NA),
    v_int64 = as.numeric(NA),
    v_float64 = as.numeric(NA),
    v_bool = as.logical(NA),
    stringsAsFactors = FALSE
  )
  ctrl_df[["function"]] <- as.character(control[["function"]] %||% "")

  for (nm in names(ctrl_df)) {
    if (!nm %in% names(node_df)) {
      node_df[[nm]] <- NA
    }
  }
  node_df <- node_df[, names(ctrl_df), drop = FALSE]
  rbind(ctrl_df, node_df)
}

`%||%` <- function(x, y) if (is.null(x) || length(x) == 0 || (length(x) == 1 && is.na(x))) y else x

safe_eval <- function(req) {
  if (nzchar(inline_code)) {
    eval(parse(text = inline_code), envir = .GlobalEnv)
  }

  fn_name <- as.character(req$control[["function"]] %||% "")
  if (!nzchar(fn_name)) {
    return(req$data)
  }

  fn <- get0(fn_name, envir = .GlobalEnv, mode = "function")
  if (is.null(fn)) {
    stop(sprintf("Function not found in R session: %s", fn_name))
  }

  if (is.list(req$data) && !is.null(names(req$data))) {
    do.call(fn, req$data)
  } else {
    fn(req$data)
  }
}

req <- read_request(request_ipc)

result_df <- tryCatch({
  value <- safe_eval(req)
  resp_control <- list()
  resp_control[["protocol_version"]] <- as.integer(req$control$protocol_version %||% 1L)
  resp_control[["call_id"]] <- as.character(req$control$call_id %||% "")
  resp_control[["function"]] <- as.character(req$control[["function"]] %||% "")
  resp_control[["script_mode"]] <- as.character(req$control$script_mode %||% "inline")
  resp_control[["script_ref"]] <- as.character(req$control$script_ref %||% "<inline>")
  resp_control[["payload_kind"]] <- as.character(req$control$payload_kind %||% "value_graph")
  resp_control[["status"]] <- "ok"
  resp_control[["result_kind"]] <- "value_graph"
  resp_control[["error_class"]] <- NA_character_
  resp_control[["error_message"]] <- NA_character_
  build_response_frame(resp_control, value)
}, error = function(e) {
  resp_control <- list()
  resp_control[["protocol_version"]] <- as.integer(req$control$protocol_version %||% 1L)
  resp_control[["call_id"]] <- as.character(req$control$call_id %||% "")
  resp_control[["function"]] <- as.character(req$control[["function"]] %||% "")
  resp_control[["script_mode"]] <- as.character(req$control$script_mode %||% "inline")
  resp_control[["script_ref"]] <- as.character(req$control$script_ref %||% "<inline>")
  resp_control[["payload_kind"]] <- as.character(req$control$payload_kind %||% "value_graph")
  resp_control[["status"]] <- "error"
  resp_control[["result_kind"]] <- NA_character_
  resp_control[["error_class"]] <- "RRuntimeError"
  resp_control[["error_message"]] <- as.character(conditionMessage(e))
  build_response_frame(resp_control, NULL)
})

write_nanoarrow(result_df, response_ipc)
