/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.test.accountlinking;

import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertNotNull;

public class TestGetUserSpeed {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testUserCreationLinkingAndGetByIdSpeeds() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        int numberOfUsers = 10000;
        List<String> userIds = new ArrayList<>();
        List<String> userIds2 = new ArrayList<>();
        {
            long start = System.currentTimeMillis();
            for (int i = 0; i < numberOfUsers; i++) {
                String email = "user" + i + "@example.com";
                AuthRecipeUserInfo user = ThirdParty.signInUp(
                        process.getProcess(), "google", "googleid" + i, email).user;
                userIds.add(user.getSupertokensUserId());
                userIds2.add(user.getSupertokensUserId());
            }
            long end = System.currentTimeMillis();
            assert end - start < 100000; // 100 sec
        }

        Thread.sleep(20000); // wait for indexing to finish

        {
            // Randomly link accounts
            long start = System.currentTimeMillis();
            while (userIds.size() > 0) {
                int numUsersToLink = new Random().nextInt(3) + 1;
                if (numUsersToLink > userIds.size()) {
                    numUsersToLink = userIds.size();
                }
                List<String> usersToLink = new ArrayList<>();
                for (int i = 0; i < numUsersToLink; i++) {
                    int index = new Random().nextInt(userIds.size());
                    usersToLink.add(userIds.get(index));
                    userIds.remove(index);
                }

                AuthRecipe.createPrimaryUser(process.getProcess(), usersToLink.get(0));
                for (int i = 1; i < usersToLink.size(); i++) {
                    AuthRecipe.linkAccounts(process.getProcess(), usersToLink.get(i), usersToLink.get(0));
                }
            }
            long end = System.currentTimeMillis();
            assert end - start < 50000; // 50 sec
        }

        Thread.sleep(20000); // wait for indexing to finish

        {
            long start = System.currentTimeMillis();
            for (String userId : userIds2) {
                AuthRecipe.getUserById(process.getProcess(), userId);
            }
            long end = System.currentTimeMillis();
            System.out.println("Time taken for " + numberOfUsers + " users: " + (end - start) + "ms");

            assert end - start < 20000;
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}