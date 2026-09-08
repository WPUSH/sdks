package wpush

import "fmt"

// Error is a WPUSH API or client error.
type Error struct {
	Message    string
	Code       *int
	HTTPStatus int
	Data       any
}

func (e *Error) Error() string {
	if e.Code != nil {
		return fmt.Sprintf("%s | code=%d | http=%d", e.Message, *e.Code, e.HTTPStatus)
	}
	return fmt.Sprintf("%s | http=%d", e.Message, e.HTTPStatus)
}

// ValidationError is raised before the HTTP request is sent.
type ValidationError struct {
	Message string
}

func (e *ValidationError) Error() string { return e.Message }
