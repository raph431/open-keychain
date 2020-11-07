/*
 * Copyright (C) 2017 Schürmann & Breitmoser GbR
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui;


import androidx.lifecycle.ViewModelProviders;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.cardview.widget.CardView;
import android.widget.ImageView;

import org.bouncycastle.util.encoders.Hex;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.daos.KeyRepository;
import org.sufficientlysecure.keychain.model.SubKey.UnifiedKeyInfo;
import org.sufficientlysecure.keychain.ui.base.BaseActivity;
import org.sufficientlysecure.keychain.ui.keyview.UnifiedKeyInfoViewModel;
import org.sufficientlysecure.keychain.ui.util.KeyFormattingUtils;
import org.sufficientlysecure.keychain.ui.util.Notify;
import org.sufficientlysecure.keychain.ui.util.Notify.Style;
import org.sufficientlysecure.keychain.ui.util.QrCodeUtils;
import org.sufficientlysecure.keychain.util.Preferences;

import java.io.IOException;
import java.util.Locale;

import timber.log.Timber;

public class QrCodeViewActivity extends BaseActivity {
    public static final String EXTRA_MASTER_KEY_ID = "master_key_id";

    private ImageView qrCodeImageView;
    private Bitmap qrCode;
    private boolean allowUseOffline;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean allowUseOffline = Preferences.getPreferences(getApplicationContext()).getExperimentalUseOffline();

        setFullScreenDialogClose(v -> ActivityCompat.finishAfterTransition(QrCodeViewActivity.this));

        qrCodeImageView = findViewById(R.id.qr_code_image);
        CardView mQrCodeLayout = findViewById(R.id.qr_code_image_layout);

        mQrCodeLayout.setOnClickListener(v -> ActivityCompat.finishAfterTransition(QrCodeViewActivity.this));

        if (!getIntent().hasExtra(EXTRA_MASTER_KEY_ID)) {
            throw new IllegalArgumentException("Missing required extra master_key_id");
        }

        UnifiedKeyInfoViewModel viewModel = ViewModelProviders.of(this).get(UnifiedKeyInfoViewModel.class);
        viewModel.setMasterKeyId(getIntent().getLongExtra(EXTRA_MASTER_KEY_ID, 0L));
        if (allowUseOffline) {
            viewModel.getUnifiedKeyInfoLiveData(getApplicationContext()).observe(this, this::onLoadUnifiedFullKey);
        } else {
            viewModel.getUnifiedKeyInfoLiveData(getApplicationContext()).observe(this, this::onLoadUnifiedKeyInfo);
        }
        qrCodeImageView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (qrCode != null) {
                Timber.d("onCreate QrCode size: %dx%d", qrCode.getWidth(), qrCode.getHeight());
                Timber.d("qrCodeImageView width: %d", qrCodeImageView.getWidth());
                int dstWidth = qrCode.getWidth() * (qrCodeImageView.getWidth() / qrCode.getWidth());
                int dstHeight = qrCode.getHeight() * (qrCodeImageView.getWidth()/qrCode.getHeight());
                Timber.d("dstSize: %dx%d", dstWidth, dstHeight);
                Bitmap scaled = Bitmap.createScaledBitmap(qrCode, dstWidth, dstHeight, false);
                qrCodeImageView.setImageBitmap(scaled);
            }
        });
    }

    private void onLoadUnifiedKeyInfo(UnifiedKeyInfo unifiedKeyInfo) {
        if (unifiedKeyInfo == null) {
            Notify.create(this, R.string.error_key_not_found, Style.ERROR).show();
            ActivityCompat.finishAfterTransition(QrCodeViewActivity.this);
            return;
        }

        Uri uri = new Uri.Builder()
                .scheme(Constants.FINGERPRINT_SCHEME)
                .opaquePart(KeyFormattingUtils.convertFingerprintToHex(unifiedKeyInfo.fingerprint()))
                .build();
        qrCode = QrCodeUtils.getQRCodeBitmap(uri, 0);

        qrCodeImageView.requestLayout();
    }

    private void onLoadUnifiedFullKey(UnifiedKeyInfo unifiedFullKey) {
        if (unifiedFullKey == null) {
            Notify.create(this, R.string.error_key_not_found, Style.ERROR).show();
            ActivityCompat.finishAfterTransition(QrCodeViewActivity.this);
            return;
        }

        KeyRepository keyRepository = KeyRepository.create(this);
        String content;
        try {
            content = keyRepository.getPublicKeyRingAsArmoredString(unifiedFullKey.master_key_id());
        } catch (KeyRepository.NotFoundException  | IOException e) {
            Notify.create(this, R.string.error_key_not_found, Notify.Style.ERROR).show();
            ActivityCompat.finishAfterTransition(QrCodeViewActivity.this);
            return;
        }

        Uri uri = new Uri.Builder()
                .scheme(Constants.KEY_SCHEME)
                .opaquePart(Hex.toHexString(content.getBytes()).toLowerCase(Locale.ENGLISH))
                .build();
        qrCode = QrCodeUtils.getQRCodeBitmap(uri, 0);
        Timber.d("onLoadUnifiedFullKey QrCode size: %dx%d", qrCode.getWidth(), qrCode.getHeight());

        qrCodeImageView.requestLayout();
    }

    @Override
    protected void initLayout() {
        setContentView(R.layout.qr_code_activity);
    }

}