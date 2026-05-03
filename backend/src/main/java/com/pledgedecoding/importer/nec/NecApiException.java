package com.pledgedecoding.importer.nec;

public class NecApiException extends RuntimeException {
	public NecApiException(String message) {
		super(message);
	}

	public NecApiException(String message, Throwable cause) {
		super(message, cause);
	}
}
