package com.fongmi.android.tv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

import java.lang.reflect.Method;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, shadows = UpdaterSignatureTest.TestSigningInfo.class)
public class UpdaterSignatureTest {

    private static final Signature A = new Signature(new byte[]{1});
    private static final Signature B = new Signature(new byte[]{2});

    @Test
    public void matchingCurrentCertificatesDoNotRequireHistory() throws Exception {
        assertTrue(matches(modern(A), modern(A)));
    }

    @Test
    public void differentCertificatesAreRejected() throws Exception {
        assertFalse(matches(modern(A), modern(B)));
    }

    @Test
    public void emptyCertificatesAreRejected() throws Exception {
        assertFalse(matches(modern(), modern()));
        assertFalse(matches(legacy(), legacy()));
    }

    @Test
    public void missingSigningInfoFallsBackToLegacyFields() throws Exception {
        assertTrue(matches(legacy(A), legacy(A)));
        assertFalse(matches(legacy(A), legacy(B)));
    }

    @Test
    public void emptyCurrentSignersFallBackToLegacyFields() throws Exception {
        PackageInfo archive = modern();
        archive.signatures = new Signature[]{A};
        assertTrue(matches(modern(A), archive));
    }

    @Test
    public void modernCurrentCertificateTakesPrecedenceOverLegacyField() throws Exception {
        PackageInfo archive = modern(B);
        archive.signatures = new Signature[]{A};
        assertFalse(matches(modern(A), archive));
    }

    @Test
    public void singleSignerRotationAcceptsOldCertificateInCandidateHistory() throws Exception {
        PackageInfo archive = modern(B);
        signing(archive).history = new Signature[]{A, B};
        assertTrue(matches(modern(A), archive));
        assertFalse(matches(archive, modern(A)));
    }

    @Test
    public void multipleSignersMustMatchAsACompleteSet() throws Exception {
        assertTrue(matches(modern(A, B), modern(B, A)));
        PackageInfo archive = modern(A);
        signing(archive).history = new Signature[]{A, B};
        assertFalse(matches(modern(A, B), archive));
        assertFalse(matches(archive, modern(A, B)));
    }

    @Test
    public void reportedMultipleSignersCannotUseHistoryEvenWithIncompleteCurrentFields() throws Exception {
        PackageInfo installed = modern(A);
        PackageInfo archive = modern(B);
        signing(archive).history = new Signature[]{A, B};
        signing(installed).multiple = true;
        assertFalse(matches(installed, archive));
        signing(installed).multiple = false;
        signing(archive).multiple = true;
        assertFalse(matches(installed, archive));
    }

    @Test
    @Config(sdk = 27)
    public void oldAndroidUsesLegacyCertificates() throws Exception {
        assertTrue(matches(legacy(A), legacy(A)));
        assertFalse(matches(legacy(A), legacy(B)));
        assertFalse(matches(legacy(), legacy()));
    }

    private boolean matches(PackageInfo installed, PackageInfo archive) throws Exception {
        Method method = Updater.class.getDeclaredMethod("signaturesMatch", PackageInfo.class, PackageInfo.class);
        method.setAccessible(true);
        return (boolean) method.invoke(Updater.create(), installed, archive);
    }

    private PackageInfo legacy(Signature... signatures) {
        PackageInfo info = new PackageInfo();
        info.packageName = "com.fongmi.android.tv.bzm";
        info.signatures = signatures;
        return info;
    }

    private PackageInfo modern(Signature... signatures) {
        PackageInfo info = legacy();
        info.signingInfo = Shadow.newInstanceOf(SigningInfo.class);
        TestSigningInfo shadow = signing(info);
        shadow.current = signatures;
        shadow.multiple = signatures.length > 1;
        return info;
    }

    private TestSigningInfo signing(PackageInfo info) {
        return Shadow.extract(info.signingInfo);
    }

    @Implements(value = SigningInfo.class, minSdk = 28)
    public static class TestSigningInfo {
        Signature[] current;
        Signature[] history;
        boolean multiple;

        @Implementation
        protected Signature[] getApkContentsSigners() {
            return current;
        }

        @Implementation
        protected Signature[] getSigningCertificateHistory() {
            return history;
        }

        @Implementation
        protected boolean hasMultipleSigners() {
            return multiple;
        }
    }
}
