package com.propbuy.exception;
import org.springframework.http.HttpStatus;
public class ApiException extends RuntimeException { private final HttpStatus status; private final String code; public ApiException(HttpStatus s,String m){this(s,m,null);} public ApiException(HttpStatus s,String m,String c){super(m);status=s;code=c;} public HttpStatus getStatus(){return status;} public String getCode(){return code;} }
