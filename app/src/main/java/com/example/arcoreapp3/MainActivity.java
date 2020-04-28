package com.example.arcoreapp3;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

public class MainActivity extends AppCompatActivity {

    private CustomArFragment fragment;
    private Anchor cloudAnchor;

    private enum AppAnchorState {
        NONE,
        HOSTING,
        HOSTED,
        RESOLVING,
        RESOLVED
    }

    private AppAnchorState appAnchorState = AppAnchorState.NONE;

    private SnackbarHelper snackbarHelper = new SnackbarHelper();

    private StorageManager storageManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragment = (CustomArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        //fragment.getPlaneDiscoveryController().hide();
        //fragment.getPlaneDiscoveryController().setInstructionView(null);
        fragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);

        Button clearButton = findViewById(R.id.clear_button);
        Button resolveButton = findViewById(R.id.resolve_button);

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCloudAnchor(null);
            }
        });

        resolveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(cloudAnchor != null) {
                    snackbarHelper.showMessageWithDismiss(getParent(), "Please clear Anchor");
                    return;
                }
                ResolveDialogFragment dialog = new ResolveDialogFragment();
                dialog.setOkListener(MainActivity.this::onResolveOkPressed);
                dialog.show(getSupportFragmentManager(), "Resolve");
            }
        });
        fragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if(plane.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING ||
                    appAnchorState != AppAnchorState.NONE) {
                        return;
                    }

                    Anchor newAnchor = fragment.getArSceneView().getSession().hostCloudAnchor(hitResult.createAnchor());

                    setCloudAnchor(newAnchor);

                    appAnchorState = AppAnchorState.HOSTING;
                    try {
                        snackbarHelper.showMessage(this, "Now hosting anchor...");
                    } catch (Exception e) {
                        Log.d("Error", e.getMessage());
                    }

                    placeObject(fragment, cloudAnchor, Uri.parse("Fox.sfb"));
                }
        );
         storageManager = new StorageManager(this);
    }


    private void onResolveOkPressed(String dialogValue) {
        int shortCode = Integer.parseInt(dialogValue);
        storageManager.getCloudAnchorID(shortCode, (cloudAnchorId) -> {
            Anchor resolvedAnchor = fragment.getArSceneView().getSession().resolveCloudAnchor(cloudAnchorId);
            setCloudAnchor(resolvedAnchor);
            placeObject(fragment, cloudAnchor, Uri.parse("Fox.sfb"));
            snackbarHelper.showMessageWithDismiss(this, "Now Resolving Anchor...");
            appAnchorState = AppAnchorState.RESOLVING;
        });

    }


    private void setCloudAnchor(Anchor newAnchor) {
        if(cloudAnchor != null) {
            cloudAnchor.detach();
        }
        cloudAnchor = newAnchor;
        appAnchorState = AppAnchorState.NONE;
        snackbarHelper.hide(this);
    }

    private void onUpdateFrame(FrameTime frameTime) {
        checkUpdatedAnchor();
    }

    private synchronized void checkUpdatedAnchor() {
        if(appAnchorState != AppAnchorState.HOSTING && appAnchorState != AppAnchorState.RESOLVING) {
            Log.d("MESSAGE: ", "Not Hosting");
            return;
        }
        Anchor.CloudAnchorState cloudState = cloudAnchor.getCloudAnchorState();
        if(appAnchorState == AppAnchorState.HOSTING) {
            if (cloudState.isError()) {
                snackbarHelper.showMessageWithDismiss(this, "Error hosting anchor..." + cloudState);
                appAnchorState = AppAnchorState.NONE;
            } else if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
                storageManager.nextShortCode((shortCode) -> {
                    if(shortCode == null) {
                        snackbarHelper.showMessageWithDismiss(this, "Could not get shortCode");
                        return;
                    }
                    storageManager.storeUsingShortCode(shortCode, cloudAnchor.getCloudAnchorId());
                    snackbarHelper.showMessageWithDismiss(this, "Anchor hosted! Cloud Short Code: " + shortCode);
                });
                appAnchorState = AppAnchorState.HOSTED;
            }
        }
        else if(appAnchorState == AppAnchorState.RESOLVING){
            if (cloudState.isError()) {
                snackbarHelper.showMessageWithDismiss(this, "Error resolving anchor..." + cloudState);
                appAnchorState = AppAnchorState.NONE;
            } else if(cloudState == Anchor.CloudAnchorState.SUCCESS) {
                snackbarHelper.showMessageWithDismiss(this, "Anchor resolved successfully");
                appAnchorState = AppAnchorState.RESOLVED;
            }
        }
    }

    private void placeObject(ArFragment fragment, Anchor anchor, Uri model) {
        ModelRenderable.builder()
                .setSource(fragment.getContext(), model)
                .build()
                .thenAccept(renderable -> addNodeToScene(fragment, anchor, renderable))
                .exceptionally((throwable -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(throwable.getMessage())
                            .setTitle("Error!");
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    return null;
                }));
    }

    private void addNodeToScene(ArFragment fragment, Anchor anchor, Renderable renderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
        node.setRenderable(renderable);
        node.setParent(anchorNode);
        fragment.getArSceneView().getScene().addChild(anchorNode);
        node.select();
    }
}
