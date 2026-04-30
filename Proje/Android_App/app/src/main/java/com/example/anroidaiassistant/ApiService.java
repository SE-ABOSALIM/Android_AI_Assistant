package com.example.anroidaiassistant;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("app-catalog")
    Call<AppCatalogResponse> syncAppCatalog(@Body AppCatalogRequest request);

    @POST("predict")
    Call<PredictResponse> predict(@Body PredictRequest request);
}
