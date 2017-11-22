package org.stepic.droid.ui.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.os.Bundle
import android.support.annotation.IdRes
import android.support.design.widget.BottomNavigationView
import android.support.v4.app.Fragment
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import com.facebook.login.LoginManager
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog
import com.vk.sdk.VKSdk
import kotlinx.android.synthetic.main.activity_main_feed.*
import org.stepic.droid.R
import org.stepic.droid.analytic.Analytic
import org.stepic.droid.base.App
import org.stepic.droid.base.Client
import org.stepic.droid.core.earlystreak.contract.EarlyStreakListener
import org.stepic.droid.core.presenters.ProfileMainFeedPresenter
import org.stepic.droid.core.presenters.StreakPresenter
import org.stepic.droid.core.presenters.UpdateAppPresenter
import org.stepic.droid.core.presenters.contracts.ProfileMainFeedView
import org.stepic.droid.core.presenters.contracts.UpdateAppView
import org.stepic.droid.fonts.FontType
import org.stepic.droid.model.Course
import org.stepic.droid.model.Profile
import org.stepic.droid.notifications.StepicInstanceIdService
import org.stepic.droid.services.UpdateWithApkService
import org.stepic.droid.ui.activities.contracts.RootScreen
import org.stepic.droid.ui.dialogs.LoadingProgressDialogFragment
import org.stepic.droid.ui.dialogs.LogoutAreYouSureDialog
import org.stepic.droid.ui.dialogs.NeedUpdatingDialog
import org.stepic.droid.ui.dialogs.TimeIntervalPickerDialogFragment
import org.stepic.droid.ui.fragments.CatalogFragment
import org.stepic.droid.ui.fragments.CertificatesFragment
import org.stepic.droid.ui.fragments.HomeFragment
import org.stepic.droid.ui.fragments.ProfileFragment
import org.stepic.droid.ui.util.TimeIntervalUtil
import org.stepic.droid.util.AppConstants
import org.stepic.droid.util.DateTimeHelper
import org.stepic.droid.util.ProgressHelper
import timber.log.Timber
import uk.co.chrisjenx.calligraphy.CalligraphyTypefaceSpan
import uk.co.chrisjenx.calligraphy.TypefaceUtils
import javax.inject.Inject


class MainFeedActivity : BackToExitActivityWithSmartLockBase(),
        BottomNavigationView.OnNavigationItemSelectedListener,
        BottomNavigationView.OnNavigationItemReselectedListener,
        LogoutAreYouSureDialog.Companion.OnLogoutSuccessListener,
        RootScreen,
        UpdateAppView,
        ProfileMainFeedView,
        EarlyStreakListener,
        TimeIntervalPickerDialogFragment.Callback {
    companion object {
        val currentIndexKey = "currentIndexKey"

        val reminderKey = "reminderKey"
        const val defaultIndex: Int = 0
        private val progressLogoutTag = "progressLogoutTag"
        private const val LOGGED_ACTION = "LOGGED_ACTION"

        const val HOME_INDEX: Int = 1
        const val CATALOG_INDEX: Int = 2
        const val PROFILE_INDEX: Int = 3
        const val CERTIFICATE_INDEX: Int = 4

        fun launchAfterLogin(sourceActivity: Activity, course: Course?) {
            val intent = Intent(sourceActivity, MainFeedActivity::class.java)
            if (course != null) {
                intent.putExtra(AppConstants.KEY_COURSE_BUNDLE, course)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.action = LOGGED_ACTION
            sourceActivity.startActivity(intent)
        }
    }

    @Inject
    lateinit var updateAppPresenter: UpdateAppPresenter

    @Inject
    lateinit var profileMainFeedPresenter: ProfileMainFeedPresenter

    @Inject
    lateinit var earlyStreakClient: Client<EarlyStreakListener>

    @Inject
    lateinit var streakPresenter: StreakPresenter

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        notificationClickedCheck(intent)

        if (intent.hasExtra(currentIndexKey)) {
            openFragment(intent)
        }
    }

    private fun notificationClickedCheck(intent: Intent) {
        val action = intent.action
        if (action != null) {
            if (action == AppConstants.OPEN_NOTIFICATION) {
                analytic.reportEvent(AppConstants.OPEN_NOTIFICATION)
            } else if (action == AppConstants.OPEN_NOTIFICATION_FOR_ENROLL_REMINDER) {
                var dayTypeString: String? = intent.getStringExtra(reminderKey)
                if (dayTypeString == null) {
                    dayTypeString = ""
                }
                analytic.reportEvent(Analytic.Notification.REMIND_OPEN, dayTypeString)
                Timber.d(Analytic.Notification.REMIND_OPEN)
                sharedPreferenceHelper.clickEnrollNotification(DateTimeHelper.nowUtc())
            } else if (action == AppConstants.OPEN_NOTIFICATION_FROM_STREAK) {
                sharedPreferenceHelper.resetNumberOfStreakNotifications()
                if (intent.hasExtra(Analytic.Streak.NOTIFICATION_TYPE_PARAM)) {
                    val notificationType = intent.getSerializableExtra(Analytic.Streak.NOTIFICATION_TYPE_PARAM) as Analytic.Streak.NotificationType
                    val bundle = Bundle()
                    bundle.putString(Analytic.Streak.NOTIFICATION_TYPE_PARAM, notificationType.name)
                    analytic.reportEvent(Analytic.Streak.STREAK_NOTIFICATION_OPENED, bundle)
                } else {
                    analytic.reportEvent(Analytic.Streak.STREAK_NOTIFICATION_OPENED)
                }
            } else if (action == AppConstants.OPEN_SHORTCUT_CATALOG) {
                analytic.reportEvent(Analytic.Shortcut.OPEN_CATALOG)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                    val shortcutManager = getSystemService(ShortcutManager::class.java)
                    shortcutManager.reportShortcutUsed(AppConstants.CATALOG_SHORTCUT_ID)
                }

            }

            //after tracking check on null user
            if (sharedPreferenceHelper.authResponseFromStore == null) {
                screenManager.openSplash(this)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.componentManager()
                .mainFeedComponent()
                .inject(this)

        setContentView(R.layout.activity_main_feed)

        notificationClickedCheck(intent)

        initGoogleApiClient(true)

        initNavigation()


        earlyStreakClient.subscribe(this)
        updateAppPresenter.attachView(this)
        updateAppPresenter.checkForUpdate()

        if (checkPlayServices() && !sharedPreferenceHelper.isGcmTokenOk) {
            threadPoolExecutor.execute {
                StepicInstanceIdService.updateAnywhere(api, sharedPreferenceHelper, analytic) //FCM!
            }
        }

        val course = courseFromExtra
        if (course != null) {
            intent.removeExtra(AppConstants.KEY_COURSE_BUNDLE)
            screenManager.showCourseDescription(this, course, true)
        }

        if (savedInstanceState == null) {
            openFragment(intent)
        }

        profileMainFeedPresenter.attachView(this)
        if (savedInstanceState == null) {
            profileMainFeedPresenter.fetchProfile()
        }
    }

    private fun openFragment(launchIntent: Intent?) {
        setFragment(R.id.home)
        val wantedIndex = launchIntent?.getIntExtra(currentIndexKey, -1) ?: -1
        when (wantedIndex) {
            CATALOG_INDEX -> navigationView.selectedItemId = R.id.catalog
            CERTIFICATE_INDEX -> navigationView.selectedItemId = R.id.certificates
            PROFILE_INDEX -> navigationView.selectedItemId = R.id.profile
            else -> {
                //do nothing
            }
        }
    }

    private fun initNavigation() {
        navigationView.setOnNavigationItemSelectedListener(this)
        navigationView.setOnNavigationItemReselectedListener(this)
    }

    private fun showCurrentFragment(@IdRes id: Int) {
        setFragment(id)
    }

    override fun onDestroy() {
        earlyStreakClient.unsubscribe(this)
        updateAppPresenter.detachView(this)
        profileMainFeedPresenter.detachView(this)
        if (isFinishing) {
            App.componentManager().releaseMainFeedComponent()
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (navigationView.selectedItemId == R.id.home) {
            finish()
            return
        } else {
            navigationView.selectedItemId = R.id.home
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AppConstants.REQUEST_EXTERNAL_STORAGE) {
            val permissionExternalStorage = permissions[0] ?: return

            if (permissionExternalStorage == Manifest.permission.WRITE_EXTERNAL_STORAGE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val link = sharedPreferenceHelper.tempLink
                if (link != null) {
                    val updateIntent = Intent(this, UpdateWithApkService::class.java)
                    updateIntent.putExtra(UpdateWithApkService.linkKey, link)
                    this.startService(updateIntent)
                }
            }
        }
    }

    override fun onNeedUpdate(linkForUpdate: String?, isAppInGp: Boolean) {
        if (isAppInGp && linkForUpdate == null) {
            return
        }
        val storedTimestamp = sharedPreferenceHelper.lastShownUpdatingMessageTimestamp
        val needUpdate = DateTimeHelper.isNeededUpdate(storedTimestamp, AppConstants.MILLIS_IN_24HOURS)
        if (!needUpdate) return

        sharedPreferenceHelper.storeLastShownUpdatingMessage()
        analytic.reportEvent(Analytic.Interaction.UPDATING_MESSAGE_IS_SHOWN)
        val dialog = NeedUpdatingDialog.newInstance(linkForUpdate, isAppInGp)
        if (!dialog.isAdded) {
            dialog.show(supportFragmentManager, null)
        }
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        sendOpenUserAnalytic(menuItem.itemId)
        showCurrentFragment(menuItem.itemId)
        return true
    }

    override fun onNavigationItemReselected(menuItem: MenuItem) {
//        scrollUp()
    }

    private fun sendOpenUserAnalytic(itemId: Int) {
        when (itemId) {
            R.id.home -> analytic.reportEvent(Analytic.Screens.USER_OPEN_MY_COURSES)
            R.id.catalog -> {
                analytic.reportEvent(Analytic.Screens.USER_OPEN_CATALOG)
                if (sharedPreferenceHelper.authResponseFromStore == null) {
                    analytic.reportEvent(Analytic.Anonymous.BROWSE_COURSES_DRAWER)
                }
            }
            R.id.certificates -> analytic.reportEvent(Analytic.Screens.USER_OPEN_CERTIFICATES);
            R.id.profile -> analytic.reportEvent(Analytic.Screens.USER_OPEN_PROFILE)
        }
    }

    private fun setFragment(@IdRes id: Int) {
        val currentFragmentTag: String? = supportFragmentManager.findFragmentById(R.id.frame)?.tag
        val nextFragment: Fragment? = when (id) {
            R.id.home -> {
                getNextFragmentOrNull(currentFragmentTag, HomeFragment::class.java.simpleName, HomeFragment.Companion::newInstance)
            }
            R.id.catalog -> {
                getNextFragmentOrNull(currentFragmentTag, CatalogFragment::class.java.simpleName, CatalogFragment.Companion::newInstance)
            }
            R.id.profile -> {
                getNextFragmentOrNull(currentFragmentTag, ProfileFragment::class.java.simpleName, ProfileFragment.Companion::newInstance)
            }
            R.id.certificates -> {
                analytic.reportEvent(Analytic.Screens.USER_OPEN_CERTIFICATES)
                getNextFragmentOrNull(currentFragmentTag, CertificatesFragment::class.java.simpleName, CertificatesFragment.Companion::newInstance)
            }
            else -> {
                null
            }
        }
        if (nextFragment != null) {
            //animation on change fragment, not for just adding
            setFragment(R.id.frame, nextFragment)
        }
    }

    private fun getNextFragmentOrNull(currentFragmentTag: String?, nextFragmentTag: String, nextFragmentCreation: () -> Fragment): Fragment? {
        if (currentFragmentTag == null || currentFragmentTag != nextFragmentTag) {
            return nextFragmentCreation.invoke()
        } else {
            return null
        }
    }

    private fun setFragment(@IdRes containerId: Int, fragment: Fragment) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(containerId, fragment, fragment.javaClass.simpleName)
        fragmentTransaction.commit()
    }

    override fun onLogout() {
        profileMainFeedPresenter.logout()
    }


    //profileMainFeedView methods
    override fun showAnonymous() {
        //stub
    }

    override fun showProfile(profile: Profile) {
        //stub
    }

    override fun showLogoutLoading() {
        val loadingProgressDialogFragment = LoadingProgressDialogFragment.newInstance()
        ProgressHelper.activate(loadingProgressDialogFragment, supportFragmentManager, progressLogoutTag)
    }

    override fun onLogoutSuccess() {
        ProgressHelper.dismiss(supportFragmentManager, progressLogoutTag)
        LoginManager.getInstance().logOut()
        VKSdk.logout()
        signOutFromGoogle()
        screenManager.showLaunchScreenAfterLogout(this)
    }

    //end profileMainFeedView methods

    //RootScreen methods
    override fun showCatalog() {
        if (navigationView.selectedItemId != R.id.catalog) {
            navigationView.selectedItemId = R.id.catalog
        }
    }

    override fun applyTransitionPrev() {
        //no-op
    }

    override fun onShowStreakSuggestion() {
        if (intent.action == LOGGED_ACTION) {
            intent.action = null


            val streakTitle = SpannableString(getString(R.string.early_notification_title))
            streakTitle.setSpan(ForegroundColorSpan(Color.BLACK), 0, streakTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val typefaceSpan = CalligraphyTypefaceSpan(TypefaceUtils.load(this.assets, fontsProvider.provideFontPath(FontType.bold)))
            streakTitle.setSpan(typefaceSpan, 0, streakTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            val description = getString(R.string.early_notification_description)

            analytic.reportEvent(Analytic.Streak.EARLY_DIALOG_SHOWN)
            val dialog = MaterialStyledDialog.Builder(this)
                    .setTitle(streakTitle)
                    .setDescription(description)
                    .setHeaderDrawable(R.drawable.dialog_background)
                    .setPositiveText(R.string.ok)
                    .setNegativeText(R.string.later_tatle)
                    .setScrollable(true, 10) // number of lines lines
                    .onPositive { _, _ ->
                        analytic.reportEvent(Analytic.Streak.EARLY_DIALOG_POSITIVE)
                        val dialogFragment = TimeIntervalPickerDialogFragment.newInstance()
                        dialogFragment.callback = this
                        if (!dialogFragment.isAdded) {
                            dialogFragment.show(supportFragmentManager, null)
                        }
                    }
                    .build()
            dialog.show()
        }
    }

    override fun onTimeIntervalPicked(data: Intent) {
        analytic.reportEvent(Analytic.Streak.EARLY_NOTIFICATION_COMPLETE)
        val intervalCode = data.getIntExtra(TimeIntervalPickerDialogFragment.resultIntervalCodeKey, TimeIntervalUtil.defaultTimeCode)
        streakPresenter.setStreakTime(intervalCode) // we do not need attach this view, because we need only set in model
    }
}