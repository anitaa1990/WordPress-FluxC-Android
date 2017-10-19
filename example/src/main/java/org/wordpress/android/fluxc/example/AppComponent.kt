package org.wordpress.android.fluxc.example

import android.app.Application
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import org.wordpress.android.fluxc.example.di.ApplicationModule
import org.wordpress.android.fluxc.module.ReleaseBaseModule
import org.wordpress.android.fluxc.module.ReleaseNetworkModule
import org.wordpress.android.fluxc.module.ReleaseOkHttpClientModule
import javax.inject.Singleton

@Singleton
@Component(modules = arrayOf(
        AndroidInjectionModule::class,
        ApplicationModule::class,
        AppSecretsModule::class,
        ReleaseOkHttpClientModule::class,
        ReleaseBaseModule::class,
        ReleaseNetworkModule::class))
interface AppComponent : AndroidInjector<ExampleApp> {
    override fun inject(app: ExampleApp)

    fun inject(activity: MainExampleActivity)
    fun inject(fragment: SitesFragment)
    fun inject(fragment: MainFragment)
    fun inject(fragment: MediaFragment)
    fun inject(fragment: CommentsFragment)
    fun inject(fragment: PostsFragment)
    fun inject(fragment: AccountFragment)
    fun inject(fragment: SignedOutActionsFragment)
    fun inject(fragment: TaxonomiesFragment)
    fun inject(fragment: ThemeFragment)
    fun inject(fragment: UploadsFragment)

    // Allows us to inject the application without having to instantiate any modules, and provides the Application
    // in the app graph
    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder

        fun build(): AppComponent
    }
}
