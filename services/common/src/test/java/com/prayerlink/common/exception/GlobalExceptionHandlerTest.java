package com.prayerlink.common.exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

public class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private WebRequest mockRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        mockRequest = mock(WebRequest.class);
        when(mockRequest.getDescription(false)).thenReturn("uri=/api/test-path");
    }

    @Test
    void handleResourceNotFoundReturnsNotFoundResponse() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Not Found Error");
        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(ex, mockRequest);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Not Found Error", response.getBody().getMessage());
        assertEquals("/api/test-path", response.getBody().getPath());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Not Found", response.getBody().getError());
    }

    @Test
    void handleUnauthorizedReturnsUnauthorizedResponse() {
        UnauthorizedException ex = new UnauthorizedException("Unauthorized Access");
        ResponseEntity<ErrorResponse> response = handler.handleUnauthorized(ex, mockRequest);
        
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Unauthorized Access", response.getBody().getMessage());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Unauthorized", response.getBody().getError());
    }

    @Test
    void handleBadRequestReturnsBadRequestResponse() {
        BadRequestException ex = new BadRequestException("Bad Request Payload");
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex, mockRequest);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Bad Request Payload", response.getBody().getMessage());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Bad Request", response.getBody().getError());
    }

    @Test
    void handleGlobalExceptionReturnsInternalServerErrorResponse() {
        Exception ex = new Exception("General Server Error");
        ResponseEntity<ErrorResponse> response = handler.handleGlobalException(ex, mockRequest);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("General Server Error", response.getBody().getMessage());
        assertEquals(500, response.getBody().getStatus());
        assertEquals("Internal Server Error", response.getBody().getError());
    }

    @Test
    void pathFormattingHandlesOtherDescriptions() {
        when(mockRequest.getDescription(false)).thenReturn("/api/plain-path");
        BadRequestException ex = new BadRequestException("Bad Request");
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex, mockRequest);
        assertEquals("/api/plain-path", response.getBody().getPath());
    }
}
