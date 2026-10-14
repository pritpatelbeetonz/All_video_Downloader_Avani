package app.messages.di

import app.messages.monetization.ads.AdsRemoteConfig
import app.messages.monetization.ads.LocalAdsRemoteConfig
import app.messages.monetization.ads.NativeAdProvider
import app.messages.monetization.ads.SampleNativeAdProvider
import app.messages.monetization.iap.BillingGateway
import app.messages.monetization.iap.EntitlementSource
import app.messages.monetization.iap.LocalBillingGateway
import app.messages.monetization.iap.LocalEntitlementSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the monetization seams. Today these resolve to local implementations
 * (entitlement = DataStore cache, remote config = default-OFF). When a
 * RevenueCat key and `google-services.json` are added, swap these bindings to
 * the RevenueCat / Firebase implementations — no feature code changes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MonetizationModule {

    @Binds
    @Singleton
    abstract fun bindEntitlementSource(impl: LocalEntitlementSource): EntitlementSource

    @Binds
    @Singleton
    abstract fun bindAdsRemoteConfig(impl: app.messages.monetization.ads.FirebaseAdsRemoteConfig): AdsRemoteConfig

    @Binds
    abstract fun bindNativeAdProvider(impl: SampleNativeAdProvider): NativeAdProvider

    @Binds
    @Singleton
    abstract fun bindBillingGateway(impl: LocalBillingGateway): BillingGateway
}
