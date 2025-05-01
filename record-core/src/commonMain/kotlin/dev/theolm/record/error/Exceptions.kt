package dev.theolm.record.error

public class NoOutputFileException(
    message: String = "No output file",
    cause: Throwable? = null
) : Exception(message, cause)

public class RecordFailException(
    message: String = "Could not record audio",
    cause: Throwable? = null
) : Exception(message, cause)

public class PermissionMissingException(
    message: String = "The required permission is missing",
    cause: Throwable? = null
) : Exception(message, cause)