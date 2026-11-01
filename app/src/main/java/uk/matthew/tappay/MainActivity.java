package uk.matthew.tappay;

import android.Manifest;
import android.nfc.NfcAdapter;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

/**
 * TapPay Playground. Everything in this app is a simulation: the card, the
 * payments, the notifications and the Wallet flow. No real card details are
 * ever read, stored, entered or transmitted. The demo PAN is a public test
 * number that is not a real account.
 */
public class MainActivity extends AppCompatActivity {

    public static final String DEMO_PAN = "4000 0000 0000 0002";
    public static final String DEMO_HOLDER = "MATTHEW CATT (DEMO)";
    public static final String DEMO_EXPIRY = "12/29";

    /** Google Wallet app package. Only used to detect and open the app. */
    private static final String WALLET_PACKAGE = "com.google.android.apps.walletnfcrel";
    private static final int REQ_POST_NOTIFICATIONS = 41;

    private NfcAdapter nfcAdapter;
    private PendingIntent nfcPendingIntent;
    private IntentFilter[] nfcIntentFilters;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextInputEditText amountInput;
    private TextView historyText;
    private final StringBuilder history = new StringBuilder();
    private androidx.appcompat.app.AlertDialog processingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        amountInput = findViewById(R.id.amount_input);
        amountInput.setText("4.99");
        historyText = findViewById(R.id.history_text);

        MaterialButton testButton = findViewById(R.id.btn_test_payment);
        testButton.setOnClickListener(v -> simulatePayment("Test button"));

        MaterialButton walletButton = findViewById(R.id.btn_wallet);
        walletButton.setOnClickListener(v -> handleWallet());

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        TextView nfcStatus = findViewById(R.id.nfc_status);
        if (nfcAdapter == null) {
            nfcStatus.setText(R.string.nfc_unavailable);
        } else {
            nfcStatus.setText(R.string.nfc_ready);
            Intent nfcIntent = new Intent(this, getClass())
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            nfcPendingIntent = PendingIntent.getActivity(
                    this, 0, nfcIntent,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            nfcIntentFilters = new IntentFilter[]{
                    new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                    new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                    new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
            };
        }

        askForNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && nfcPendingIntent != null) {
            nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, nfcIntentFilters, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        boolean isTag = NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action);
        if (isTag) {
            // A tag was tapped while the app was open. Fire the demo flow.
            // The app never reads payment data from the tag: only the OS-level
            // tag-discovered event is used as a trigger.
            simulatePayment("NFC tap");
        }
    }

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
        }
    }

    private Double readAmount() {
        if (amountInput == null || amountInput.getText() == null) return null;
        String raw = amountInput.getText().toString().trim();
        if (raw.isEmpty()) return null;
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
        if (value <= 0 || value > 999999) return null;
        return value;
    }

    /** Runs the fake payment flow for any trigger: test button, NFC tap, Wallet demo. */
    private void simulatePayment(String source) {
        Double amount = readAmount();
        if (amount == null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("SIMULATION - amount needed")
                    .setMessage("Enter a demo amount in GBP first, for example 4.99.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        final double amountFinal = amount;
        processingDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sim_processing_title))
                .setMessage(getString(R.string.sim_processing_body, source))
                .setCancelable(false)
                .show();
        handler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            showResult(amountFinal, source);
        }, 1200);
    }

    private void showResult(double amount, String source) {
        String formatted = String.format(Locale.UK, "£%.2f", amount);
        if (processingDialog != null) {
            processingDialog.dismiss();
            processingDialog = null;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sim_approved_title))
                .setMessage(getString(R.string.sim_approved_body, formatted, DEMO_PAN, source))
                .setPositiveButton(android.R.string.ok, null)
                .show();
        NotificationHelper.postDemoPaymentNotification(this, formatted, source);
        appendHistory(formatted, source);
    }

    private void appendHistory(String formatted, String source) {
        history.insert(0, "• " + formatted + " · " + source + " · SIMULATED\n");
        historyText.setText(history.toString());
    }

    /**
     * Google Wallet: third-party apps are not allowed into the tap-to-pay
     * stack. The only official integration is the Save to Google Wallet
     * passes API, which needs a real issuer account and service credentials.
     * So this app detects and opens Wallet when it is installed, and falls
     * back to a clearly-labelled demo otherwise. It never touches card data.
     */
    private void handleWallet() {
        boolean installed;
        try {
            getPackageManager().getPackageInfo(WALLET_PACKAGE, 0);
            installed = true;
        } catch (PackageManager.NameNotFoundException e) {
            installed = false;
        }
        if (installed) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Google Wallet found")
                    .setMessage(getString(R.string.wallet_found_body))
                    .setPositiveButton("Open Wallet", (d, w) -> {
                        Intent launch = getPackageManager()
                                .getLaunchIntentForPackage(WALLET_PACKAGE);
                        if (launch != null) startActivity(launch);
                    })
                    .setNegativeButton("Demo fallback", (d, w) -> showWalletDemoFallback())
                    .show();
        } else {
            showWalletDemoFallback();
        }
    }

    private void showWalletDemoFallback() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("SIMULATION - demo wallet fallback")
                .setMessage(getString(R.string.wallet_demo_body))
                .setPositiveButton(android.R.string.ok, null)
                .show();
        NotificationHelper.postDemoPaymentNotification(this, "£0.00",
                "Wallet demo fallback");
        appendHistory("£0.00", "Wallet demo fallback");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_about) {
            showAbout();
            return true;
        } else if (id == R.id.action_clear) {
            history.setLength(0);
            historyText.setText(R.string.history_empty);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.app_name))
                .setMessage(getString(R.string.about_body))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
