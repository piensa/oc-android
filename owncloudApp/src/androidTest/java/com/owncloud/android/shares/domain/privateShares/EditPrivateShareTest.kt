/**
 * ownCloud Android client application
 *
 * @author David González Verdugo
 * Copyright (C) 2019 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.shares.domain.privateShares

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.owncloud.android.R
import com.owncloud.android.authentication.AccountAuthenticator
import com.owncloud.android.capabilities.db.OCCapability
import com.owncloud.android.capabilities.viewmodel.OCCapabilityViewModel
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.lib.common.accounts.AccountUtils
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.shares.ShareType
import com.owncloud.android.lib.resources.status.CapabilityBooleanType
import com.owncloud.android.lib.resources.status.OwnCloudVersion
import com.owncloud.android.shares.domain.OCShare
import com.owncloud.android.shares.presentation.OCShareViewModel
import com.owncloud.android.shares.presentation.ShareActivity
import com.owncloud.android.ui.activity.FileActivity
import com.owncloud.android.utils.AccountsManager
import com.owncloud.android.utils.Permissions
import com.owncloud.android.utils.TestUtil
import com.owncloud.android.vo.Resource
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class EditPrivateShareTest {
    @Rule
    @JvmField
    val activityRule = ActivityTestRule(
        ShareActivity::class.java,
        true,
        false
    )

    private lateinit var file: OCFile

    private val privateShare = TestUtil.createPrivateShare(
        shareType = ShareType.GROUP.value,
        shareWith = "friends",
        path = "/Photos",
        permissions = Permissions.READ_PERMISSIONS.value,
        isFolder = true,
        sharedWithDisplayName = "Friends"
    )

    private val capabilitiesLiveData = MutableLiveData<Resource<OCCapability>>()
    private val privateSharesLiveData = MutableLiveData<Resource<List<OCShare>>>()

    private val ocCapabilityViewModel = Mockito.mock(OCCapabilityViewModel::class.java)
    private val ocShareViewModel = Mockito.mock(OCShareViewModel::class.java)

    companion object {
        private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        private val account = Account("admin", "owncloud")

        @BeforeClass
        @JvmStatic
        fun init() {
            addAccount()
        }

        @AfterClass
        @JvmStatic
        fun cleanUp() {
            AccountsManager.deleteAllAccounts(targetContext)
        }

        private fun addAccount() {
            // obtaining an AccountManager instance
            val accountManager = AccountManager.get(targetContext)

            accountManager.addAccountExplicitly(account, "a", null)

            // include account version, user, server version and token with the new account
            accountManager.setUserData(
                account,
                AccountUtils.Constants.KEY_OC_VERSION,
                OwnCloudVersion("10.2").toString()
            )
            accountManager.setUserData(
                account,
                AccountUtils.Constants.KEY_OC_BASE_URL,
                "serverUrl:port"
            )
            accountManager.setUserData(
                account,
                AccountUtils.Constants.KEY_DISPLAY_NAME,
                "admin"
            )
            accountManager.setUserData(
                account,
                AccountUtils.Constants.KEY_OC_ACCOUNT_VERSION,
                "1"
            )

            accountManager.setAuthToken(
                account,
                AccountAuthenticator.KEY_AUTH_TOKEN_TYPE,
                "AUTH_TOKEN"
            )
        }
    }

    @Before
    fun setUp() {
        val intent = Mockito.spy(Intent::class.java)

        file = TestUtil.createFile("image.jpg")

        `when`(intent.getParcelableExtra(FileActivity.EXTRA_FILE) as? Parcelable).thenReturn(file)
        intent.putExtra(FileActivity.EXTRA_FILE, file)

        `when`(ocCapabilityViewModel.getCapabilityForAccount(false)).thenReturn(capabilitiesLiveData)
        `when`(ocCapabilityViewModel.getCapabilityForAccount(true)).thenReturn(capabilitiesLiveData)
        `when`(ocShareViewModel.getPrivateShares(file.remotePath)).thenReturn(privateSharesLiveData)
        `when`(ocShareViewModel.getPrivateShare(1)).thenReturn(
            MutableLiveData<OCShare>().apply {
                postValue(privateShare)
            }
        )
        `when`(ocShareViewModel.getPublicShares(file.remotePath)).thenReturn(MutableLiveData())

        stopKoin()

        startKoin {
            androidContext(ApplicationProvider.getApplicationContext<Context>())
            modules(
                module(override = true) {
                    viewModel {
                        ocCapabilityViewModel
                    }
                    viewModel {
                        ocShareViewModel
                    }
                }
            )
        }

        activityRule.launchActivity(intent)
    }

    @After
    fun clean() {
        stopKoin()
    }

    @Test
    fun editPrivateShareLoading() {
        loadCapabilitiesSuccessfully()
        loadPrivateSharesSuccessfully()

        `when`(
            ocShareViewModel.updatePrivateShare(
                1,
                Permissions.EDIT_PERMISSIONS.value
            )
        ).thenReturn(
            MutableLiveData<Resource<Unit>>().apply {
                postValue(Resource.loading())
            }
        )

        onView(withId(R.id.editShareButton)).perform(click())
        onView(withId(R.id.canEditSwitch)).perform(click())

        onView(withText(R.string.common_loading)).check(matches(isDisplayed()))
    }

    @Test
    fun editPrivateShareError() {
        loadCapabilitiesSuccessfully()
        loadPrivateSharesSuccessfully()

        `when`(
            ocShareViewModel.updatePrivateShare(
                1,
                Permissions.EDIT_PERMISSIONS.value
            )
        ).thenReturn(
            MutableLiveData<Resource<Unit>>().apply {
                postValue(
                    Resource.error(
                        RemoteOperationResult.ResultCode.HOST_NOT_AVAILABLE
                    )
                )
            }
        )

        onView(withId(R.id.editShareButton)).perform(click())
        onView(withId(R.id.canEditSwitch)).perform(click())

        onView(withText(R.string.network_host_not_available)).check(matches(isDisplayed()))
    }

    private fun loadCapabilitiesSuccessfully(
        capability: OCCapability = TestUtil.createCapability(
            versionString = "10.1.1",
            sharingPublicMultiple = CapabilityBooleanType.TRUE.value
        )
    ) {
        capabilitiesLiveData.postValue(
            Resource.success(
                capability
            )
        )
    }

    private fun loadPrivateSharesSuccessfully(privateShares: ArrayList<OCShare> = arrayListOf(privateShare)) {
        privateSharesLiveData.postValue(Resource.success(privateShares))
    }
}
