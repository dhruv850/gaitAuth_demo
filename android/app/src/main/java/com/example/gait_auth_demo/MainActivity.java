package com.example.gait_auth_demo;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import java.lang.reflect.Type;
import java.util.ArrayList;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

import id.unify.sdk.core.CompletionHandler;
import id.unify.sdk.core.UnifyID;
import id.unify.sdk.core.UnifyIDConfig;
import id.unify.sdk.core.UnifyIDException;
import id.unify.sdk.gaitauth.AuthenticationListener;
import id.unify.sdk.gaitauth.AuthenticationResult;
import id.unify.sdk.gaitauth.Authenticator;
import id.unify.sdk.gaitauth.FeatureEventListener;
import id.unify.sdk.gaitauth.GaitAuth;
import id.unify.sdk.gaitauth.GaitAuthException;
import id.unify.sdk.gaitauth.GaitFeature;
import id.unify.sdk.gaitauth.GaitModel;
import id.unify.sdk.gaitauth.GaitModelException;
import id.unify.sdk.gaitauth.GaitQuantileConfig;

public class MainActivity extends FlutterActivity {
    private static final String CHANNEL = "example.com/demo";
    ArrayList<GaitFeature> featureList;
    public String modelID;
    public GaitModel.Status gaitModelStatus = GaitModel.Status.UNKNOWN;
    public AuthenticationResult.Status authStatus = AuthenticationResult.Status.INCONCLUSIVE;
    GaitModel gaitModel;
    GaitAuth gaitAuth;
    Authenticator authenticator;


    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        loadData();
        loadModelId();
        initGaitAuth();

        new MethodChannel(getFlutterEngine().getDartExecutor().getBinaryMessenger(), CHANNEL).setMethodCallHandler(new MethodChannel.MethodCallHandler() {  //platform channel
            @Override
            public void onMethodCall(MethodCall methodCall, MethodChannel.Result result) {

                if (methodCall.method.equals("getMessage")) {
                    if (gaitModelStatus == GaitModel.Status.READY) {
                        getAuthStatus();
                    }
                    if (authStatus == AuthenticationResult.Status.AUTHENTICATED) {
                        String textResult = "Authentication Successful";
                        result.success(textResult);
                    } else {
                        String textResult = "You are not authorised";
                        result.success(textResult);
                    }

                }
            }
        });

    }


    public void initGaitAuth() {
        UnifyID.initialize(getApplicationContext(), "", "", new CompletionHandler() {
            @Override
            public void onCompletion(UnifyIDConfig config) {
                gaitAuth.initialize(getApplicationContext(), config);

                try {
                    if (!TextUtils.isEmpty(modelID)) {
                        //check if we already have modelID stored, if so, use it.
                        loadAndStartAuthenticator();
                    } else {
                        // if not, create a new Model
                        gaitModel = gaitAuth.getInstance().createModel();
                    }
                    if (featureList.size() <= 100 && gaitModelStatus != GaitModel.Status.TRAINING) {
                        startCollection();
                    }
                } catch (GaitAuthException e) {
                    Log.d("caught exception", "authenticator");
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(UnifyIDException e) {
                // Initialization failed
                Log.d("caught exception", "failed Init");
            }
        });
    }

    public void loadAndStartAuthenticator() throws GaitModelException {
        gaitModel = gaitAuth.getInstance().loadModel(modelID);
        gaitModel.refresh();

        if (gaitModel.getStatus() == GaitModel.Status.READY) {
            double QUANTILE_THRESHOLD = 0.8; // your desire quantiled threshold
            GaitQuantileConfig config1 = new GaitQuantileConfig(QUANTILE_THRESHOLD);
            config1.setMinNumScores(5);
            config1.setMaxNumScores(50);
            config1.setMaxScoreAge(300);
            config1.setNumQuantiles(100);
            config1.setQuantile(100);
            try {
                authenticator = gaitAuth.getInstance().createAuthenticator(config1, gaitModel);
            } catch (GaitAuthException e) {
                e.printStackTrace();
            }
        }
    }

    private void startCollection() throws GaitAuthException {

        gaitAuth.getInstance().registerListener(new FeatureEventListener() {
            @Override
            public void onNewFeature(GaitFeature feature) {
                featureList.add(feature);
                Log.d("feature added", feature.toString());
                saveData();
                modelID = gaitModel.getId();
                saveModelId();
                if (featureList.size() >= 1000) {
                    try {
                        gaitModel.add(featureList);
                        modelID = gaitModel.getId();
                        saveModelId();
                        gaitModel.train();
                        gaitAuth.getInstance().unregisterAllListeners(); // after model is being trained stopping collecting features as it gives error

                    } catch (GaitModelException e) {
                        Log.d("caught exception :", "failed training" + e);
                    }
                }
            }
        });
    }

    private void getAuthStatus() {

        authenticator.getStatus(new AuthenticationListener() {
            @Override
            public void onComplete(AuthenticationResult authenticationResult) {
                // Authentication status obtained
                Log.d("Authentication Result", authenticationResult.getStatus().toString());  // result to string
                authStatus = authenticationResult.getStatus();
            }

            @Override
            public void onFailure(GaitAuthException e) {
                Log.d("Authentication Failure", "Please check your authenticator " + e);
            }
        });
    }

    private void saveData() {    //to save features list
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(featureList);
        editor.putString("feature List", json);
        editor.apply();
    }

    private void loadData() { // to load features list in the start
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE);
        Gson gson = new Gson();
        String json = sharedPreferences.getString("feature List", null);
        Type type = new TypeToken<ArrayList<GaitFeature>>() {
        }.getType();
        featureList = gson.fromJson(json, type);
        if (featureList == null) {
            featureList = new ArrayList<GaitFeature>();
        }
    }

    public void saveModelId() { //to save model ID
        SharedPreferences sharedPreferences = getSharedPreferences("SharedPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(modelID);
        editor.putString("Saved ModelID", json);
        editor.apply();
    }

    public void loadModelId() { // to load Model ID in the start
        SharedPreferences sharedPreferences = getSharedPreferences("SharedPrefs", MODE_PRIVATE);
        Gson gson = new Gson();
        String json = sharedPreferences.getString("Saved ModelID", null);
        Type type = new TypeToken<String>() {
        }.getType();
        modelID = gson.fromJson(json, type);
        if (modelID == null) {
            modelID = new String();
        }
    }
}
