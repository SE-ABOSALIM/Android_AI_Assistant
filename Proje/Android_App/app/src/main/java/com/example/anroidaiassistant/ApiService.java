package com.example.anroidaiassistant;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Path;
import retrofit2.http.POST;

public interface ApiService {
    @POST("app-catalog")
    Call<AppCatalogResponse> syncAppCatalog(@Body AppCatalogRequest request);

    @DELETE("app-catalog/{session_id}")
    Call<Void> closeAppCatalog(@Path("session_id") String sessionId);

    @POST("predict")
    Call<PredictResponse> predict(@Body PredictRequest request);
}
