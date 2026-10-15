package com.propbuy.exception; import org.springframework.http.HttpStatus; public class ConflictException extends ApiException { public ConflictException(String m){super(HttpStatus.CONFLICT,m);} }
