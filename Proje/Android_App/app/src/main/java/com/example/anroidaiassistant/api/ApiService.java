package com.example.anroidaiassistant.api;

import com.example.anroidaiassistant.api.dto.AppCatalogRequest;
import com.example.anroidaiassistant.api.dto.AppCatalogResponse;
import com.example.anroidaiassistant.api.dto.AppCatalogStatusResponse;
import com.example.anroidaiassistant.api.dto.CommandHistoryResponse;
import com.example.anroidaiassistant.api.dto.CommandHistoryMutationResponse;
import com.example.anroidaiassistant.api.dto.CustomCommandListResponse;
import com.example.anroidaiassistant.api.dto.CustomCommandMutationRequest;
import com.example.anroidaiassistant.api.dto.CustomCommandMutationResponse;
import com.example.anroidaiassistant.api.dto.PredictRequest;
import com.example.anroidaiassistant.api.dto.PredictResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

public interface ApiService {
    @POST("app-catalog")
    Call<AppCatalogResponse> syncAppCatalog(@Body AppCatalogRequest request);

    @GET("app-catalog/{session_id}")
    Call<AppCatalogStatusResponse> getAppCatalogStatus(@Path("session_id") String sessionId);

    @DELETE("app-catalog/{session_id}")
    Call<Void> closeAppCatalog(@Path("session_id") String sessionId);

    @POST("predict")
    Call<PredictResponse> predict(@Body PredictRequest request);

    @GET("command-history")
    Call<CommandHistoryResponse> getCommandHistory(
            @Query("session_id") String sessionId,
            @Query("device_id") String deviceId,
            @Query("limit") int limit,
            @Query("offset") int offset,
            @Query("q") String query
    );

    @DELETE("command-history")
    Call<CommandHistoryMutationResponse> clearCommandHistory(
            @Query("session_id") String sessionId,
            @Query("device_id") String deviceId
    );

    @DELETE("command-history/{history_id}")
    Call<CommandHistoryMutationResponse> deleteCommandHistoryItem(
            @Path("history_id") String historyId,
            @Query("session_id") String sessionId,
            @Query("device_id") String deviceId
    );

    @GET("custom-commands")
    Call<CustomCommandListResponse> getCustomCommands(
            @Query("device_id") String deviceId,
            @Query("language") String language
    );

    @POST("custom-commands")
    Call<CustomCommandMutationResponse> createCustomCommand(
            @Body CustomCommandMutationRequest request
    );

    @PUT("custom-commands/{command_id}")
    Call<CustomCommandMutationResponse> updateCustomCommand(
            @Path("command_id") String commandId,
            @Body CustomCommandMutationRequest request
    );

    @DELETE("custom-commands/{command_id}")
    Call<CustomCommandMutationResponse> deleteCustomCommand(
            @Path("command_id") String commandId,
            @Query("device_id") String deviceId
    );
}
