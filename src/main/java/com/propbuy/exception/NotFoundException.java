package com.propbuy.exception; import org.springframework.http.HttpStatus; public class NotFoundException extends ApiException { public NotFoundException(String m){super(HttpStatus.NOT_FOUND,m);} }
