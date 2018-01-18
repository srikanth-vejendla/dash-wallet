/*
 * Copyright 2011-2015 the original author or authors.
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

package de.schildbach.wallet.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;

import org.bitcoinj.core.Address;
import org.bitcoinj.wallet.Wallet;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.integration.android.BitcoinIntegration;
import de.schildbach.wallet.ui.send.SendCoinsActivity;

/**
 * The only purpose of this Activity is to handle all so called Wallet Uris
 * providing simple and convenient Inter App Communication.
 * It could not be handled directly by WalletActivity, since it is configured
 * as a singleTask and doesn't support startActivityForResult(...) pattern.
 */
public final class WalletUriHandlerActivity extends Activity {

    private static final int REQUEST_CODE_SEND_FROM_WALLET_URI = 1;

    private Wallet wallet;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WalletApplication application = (WalletApplication) getApplication();
        wallet = application.getWallet();

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(getIntent());
    }

    private void handleIntent(final Intent intent) {
        if (wallet == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        final String action = intent.getAction();
        final Uri intentUri = intent.getData();
        final String scheme = intentUri != null ? intentUri.getScheme() : null;

        if (Intent.ACTION_VIEW.equals(action) && Constants.WALLET_URI_SCHEME.equals(scheme)) {

            new InputParser.WalletUriParser(intentUri) {
                @Override
                protected void handlePaymentIntent(final PaymentIntent paymentIntent, boolean forceInstantSend) {
                    SendCoinsActivity.sendFromWalletUri(
                            WalletUriHandlerActivity.this, REQUEST_CODE_SEND_FROM_WALLET_URI, paymentIntent, forceInstantSend);
                }

                @Override
                protected void handleMasterPublicKeyRequest() {
                    String watchingKey = wallet.getWatchingKey().serializePubB58(wallet.getNetworkParameters());
                    Uri requestData = getIntent().getData();
                    Intent result = WalletUri.createMasterPublicKeyResult(requestData, watchingKey, null, getAppName());
                    setResult(RESULT_OK, result);
                    finish();
                }

                @Override
                protected void handleAddressRequest() {
                    Address address = wallet.currentReceiveAddress();
                    Uri requestData = getIntent().getData();
                    Intent result = WalletUri.createAddressResult(requestData, address.toBase58(), getAppName());
                    setResult(RESULT_OK, result);
                    finish();
                }

                @Override
                protected void error(final int messageResId, final Object... messageArgs) {
                    dialog(WalletUriHandlerActivity.this, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    }, 0, messageResId, messageArgs);
                }

                private String getAppName() {
                    ApplicationInfo applicationInfo = getApplicationInfo();
                    int stringId = applicationInfo.labelRes;
                    return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : getString(stringId);
                }
            }.parse();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SEND_FROM_WALLET_URI) {
            Intent result = null;
            if (resultCode == Activity.RESULT_OK) {
                Uri requestData = getIntent().getData();
                String transactionHash = BitcoinIntegration.transactionHashFromResult(data);
                result = WalletUri.createPaymentResult(requestData, transactionHash);
            }
            setResult(resultCode, result);
            finish();
        }
    }
}
