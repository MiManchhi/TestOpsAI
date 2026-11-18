class ParserError(Exception):
    def __init__(self, status: int, code: str, message: str) -> None:
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message


def invalid_content(message: str = "Content cannot be parsed") -> ParserError:
    return ParserError(422, "UNPROCESSABLE_CONTENT", message)
