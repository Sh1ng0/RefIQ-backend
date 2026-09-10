package com.refiq.platform.calculation.api.web.response;



import com.refiq.platform.shared.web.ApiError;

public sealed interface ResultWebResponse {


  record Success(ResultResponse data) implements ResultWebResponse {}


  record Failure(ApiError error) implements ResultWebResponse {}
}