package app.aaps.plugins.main.di

import app.aaps.core.interfaces.skin.SkinDescriptionProvider
import app.aaps.plugins.main.skins.SkinInterface
import app.aaps.plugins.main.skins.SkinMinimal
import app.aaps.plugins.main.skins.SkinProvider
import app.aaps.plugins.main.skins.SkinProviderImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Qualifier

/**
 * AIMI/OpenAps: single dashboard-oriented skin ([SkinMinimal]) after upstream removed multi-skin wiring.
 * Keeps [SkinProvider] for Compose home routing and skin list preferences.
 */
@Module(includes = [SkinsModule.Bindings::class])
@InstallIn(SingletonComponent::class)
open class SkinsModule {

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {
        @Binds
        fun bindSkinProvider(impl: SkinProviderImpl): SkinProvider

        @Binds
        fun bindSkinDescriptionProvider(impl: SkinProviderImpl): SkinDescriptionProvider
    }

    @Provides
    @Skin
    @IntoMap
    @IntKey(0)
    fun bindsSkinMinimal(skinMinimal: SkinMinimal): SkinInterface = skinMinimal

    @Qualifier
    annotation class Skin
}
