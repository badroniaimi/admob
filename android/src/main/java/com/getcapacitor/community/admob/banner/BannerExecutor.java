package com.getcapacitor.community.admob.banner;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.util.Supplier;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.getcapacitor.community.admob.helpers.AdViewIdHelper;
import com.getcapacitor.community.admob.helpers.RequestHelper;
import com.getcapacitor.community.admob.models.AdMobPluginError;
import com.getcapacitor.community.admob.models.AdOptions;
import com.getcapacitor.community.admob.models.Executor;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.common.util.BiConsumer;

public class BannerExecutor extends Executor {

    private final JSObject emptyObject = new JSObject();
    private RelativeLayout mAdViewLayout;
    private AdView mAdView;
    private ViewGroup mViewGroup;

    public BannerExecutor(
        Supplier<Context> contextSupplier,
        Supplier<Activity> activitySupplier,
        BiConsumer<String, JSObject> notifyListenersFunction,
        String pluginLogTag
    ) {
        super(contextSupplier, activitySupplier, notifyListenersFunction, pluginLogTag, "BannerExecutor");
    }

    public void initialize() {
        try {
            mViewGroup = (ViewGroup) ((ViewGroup) activitySupplier.get().findViewById(android.R.id.content)).getChildAt(0);
        } catch (Exception ex) {
            Log.d(logTag, "Failed initializing BannerExecutor");
        }
    }

    public void showBanner(final PluginCall call) {
        try {
            final AdOptions adOptions = AdOptions.getFactory().createBannerOptions(call);
            float widthPixels = (int) contextSupplier.get().getResources().getDisplayMetrics().widthPixels;
            float density = contextSupplier.get().getResources().getDisplayMetrics().density;

            if (mAdView != null) {
                updateExistingAdView(adOptions);
                return;
            }
            mAdView = new AdView(contextSupplier.get());

            if (!adOptions.adSize.toString().equals("ADAPTIVE_BANNER")) {
                mAdView.setAdSize(adOptions.adSize.getSize());
            } else {
                // ADAPTIVE BANNER
                mAdView.setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(contextSupplier.get(), (int) (widthPixels / density))
                );
            }

            // Setup AdView Layout
            mAdViewLayout = new RelativeLayout(contextSupplier.get());
            mAdViewLayout.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
            mAdViewLayout.setVerticalGravity(Gravity.BOTTOM);

            final CoordinatorLayout.LayoutParams mAdViewLayoutParams = new CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
            );

            // TODO: Make an enum like the AdSizeEnum?
            switch (adOptions.position) {
                case "TOP_CENTER":
                    mAdViewLayoutParams.gravity = Gravity.TOP;
                    break;
                case "CENTER":
                    mAdViewLayoutParams.gravity = Gravity.CENTER;
                    break;
                default:
                    mAdViewLayoutParams.gravity = Gravity.BOTTOM;
                    break;
            }

            mAdViewLayout.setLayoutParams(mAdViewLayoutParams);

            int densityMargin = (int) (adOptions.margin * density);

            // Center Banner Ads
            int adWidth = (int) (adOptions.adSize.getSize().getWidth() * density);
            int sideMargin = ((int) widthPixels - adWidth) / 2;

            if (adWidth <= 0 || adOptions.adSize.toString().equals("ADAPTIVE_BANNER")) {
                mAdViewLayoutParams.setMargins(0, densityMargin, 0, densityMargin);
            } else {
                mAdViewLayoutParams.setMargins(sideMargin, densityMargin, sideMargin, densityMargin);
            }

            createNewAdView(adOptions);

            call.resolve();
        } catch (Exception ex) {
            call.reject(ex.getLocalizedMessage(), ex);
        }
    }

    public void hideBanner(final PluginCall call) {
        try {
            if (mAdView == null) {
                call.reject("You tried to hide a banner that was never shown");
                return;
            }
            activitySupplier
                .get()
                .runOnUiThread(
                    () -> {
                        try {
                            if (mAdViewLayout != null) {
                                mAdViewLayout.setVisibility(View.GONE);
                                mAdView.pause();

                                final BannerAdSizeInfo sizeInfo = new BannerAdSizeInfo(0, 0);

                                notifyListeners(BannerAdPluginEvents.SizeChanged.getWebEventName(), sizeInfo);

                                call.resolve();
                            }
                        } catch (Exception ex) {
                            Log.d(logTag, "failed to hide banner ad");
                        }
                    }
                );
        } catch (Exception ex) {
            call.reject(ex.getLocalizedMessage(), ex);
        }
    }

    public void resumeBanner(final PluginCall call) {
        try {
            activitySupplier
                .get()
                .runOnUiThread(
                    () -> {
                        if (mAdViewLayout != null && mAdView != null) {
                            mAdViewLayout.setVisibility(View.VISIBLE);
                            mAdView.resume();

                            final BannerAdSizeInfo sizeInfo = new BannerAdSizeInfo(mAdView);
                            notifyListeners(BannerAdPluginEvents.SizeChanged.getWebEventName(), sizeInfo);

                            Log.d(logTag, "Banner AD Resumed");
                        }
                    }
                );

            call.resolve();
        } catch (Exception ex) {
            call.reject(ex.getLocalizedMessage(), ex);
        }
    }

    public void removeBanner(final PluginCall call) {
        try {
            if (mAdView != null) {
                activitySupplier
                    .get()
                    .runOnUiThread(
                        () -> {
                            try {
                                if (mAdView != null) {
                                    mViewGroup.removeView(mAdViewLayout);
                                    mAdViewLayout.removeView(mAdView);
                                    mAdView.destroy();
                                    mAdView = null;
                                    Log.d(logTag, "Banner AD Removed");
                                    final BannerAdSizeInfo sizeInfo = new BannerAdSizeInfo(0, 0);
                                    notifyListeners(BannerAdPluginEvents.SizeChanged.getWebEventName(), sizeInfo);
                                }
                            } catch (Exception ex) {
                                Log.d(logTag, "Failed removing banner ad");
                            }
                        }
                    );
            }

            call.resolve();
        } catch (Exception ex) {
            call.reject(ex.getLocalizedMessage(), ex);
        }
    }

    private void updateExistingAdView(AdOptions adOptions) {
        try {
            if (activitySupplier != null && activitySupplier.get() != null) {
                activitySupplier
                    .get()
                    .runOnUiThread(
                        () -> {
                            try {
                                final AdRequest adRequest = RequestHelper.createRequest(adOptions);
                                if (mAdView != null) mAdView.loadAd(adRequest);
                            } catch (Exception ex) {
                                Log.d(logTag, "Failed updating existing AdView");
                            }
                        }
                    );
            }
        } catch (Exception ex) {
            Log.d(logTag, "Failed updating existing AdView");
        }
    }

    /**
     * Follow iOS method Name:
     * https://developers.google.com/admob/ios/banner?hl=ja
     */
    private void createNewAdView(AdOptions adOptions) {
        try {
            // Run AdMob In Main UI Thread
            activitySupplier
                .get()
                .runOnUiThread(
                    () -> {
                        try {
                            final AdRequest adRequest = RequestHelper.createRequest(adOptions);
                            // Assign the correct id needed
                            AdViewIdHelper.assignIdToAdView(mAdView, adOptions, adRequest, logTag, contextSupplier.get());
                            // Add the AdView to the view hierarchy.
                            mAdViewLayout.addView(mAdView);
                            // Start loading the ad.
                            mAdView.loadAd(adRequest);
                            mAdView.setAdListener(
                                new AdListener() {
                                    @Override
                                    public void onAdLoaded() {
                                        final BannerAdSizeInfo sizeInfo = new BannerAdSizeInfo(mAdView);

                                        notifyListeners(BannerAdPluginEvents.SizeChanged.getWebEventName(), sizeInfo);
                                        notifyListeners(BannerAdPluginEvents.Loaded.getWebEventName(), emptyObject);
                                        super.onAdLoaded();
                                    }

                                    @Override
                                    public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                                        if (mAdView != null) {
                                            mViewGroup.removeView(mAdViewLayout);
                                            mAdViewLayout.removeView(mAdView);
                                            mAdView.destroy();
                                            mAdView = null;
                                        }

                                        final BannerAdSizeInfo sizeInfo = new BannerAdSizeInfo(0, 0);
                                        notifyListeners(BannerAdPluginEvents.SizeChanged.getWebEventName(), sizeInfo);

                                        final AdMobPluginError adMobPluginError = new AdMobPluginError(adError);
                                        notifyListeners(BannerAdPluginEvents.FailedToLoad.getWebEventName(), adMobPluginError);

                                        super.onAdFailedToLoad(adError);
                                    }

                                    @Override
                                    public void onAdOpened() {
                                        notifyListeners(BannerAdPluginEvents.Opened.getWebEventName(), emptyObject);
                                        super.onAdOpened();
                                    }

                                    @Override
                                    public void onAdClosed() {
                                        notifyListeners(BannerAdPluginEvents.Closed.getWebEventName(), emptyObject);
                                        super.onAdClosed();
                                    }

                                    @Override
                                    public void onAdImpression() {
                                        notifyListeners(BannerAdPluginEvents.AdImpression.getWebEventName(), emptyObject);
                                        super.onAdImpression();
                                    }
                                }
                            );

                            // Add AdViewLayout top of the WebView
                            mViewGroup.addView(mAdViewLayout);
                        } catch (Exception ex) {
                            Log.d(logTag, "Failed creating new AdView");
                        }
                    }
                );
        } catch (Exception ex) {
            Log.d(logTag, "Failed creating new AdView");
        }
    }
}
