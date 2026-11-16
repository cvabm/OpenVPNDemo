package de.blinkt.openvpn.activities;

import static de.blinkt.openvpn.LaunchVPN.CLEARLOG;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.IServiceStatus;
import de.blinkt.openvpn.core.PasswordCache;
import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.fragments.Utils;

public class VpnActivity extends AppCompatActivity implements VpnStatus.StateListener {
    private static final int START_VPN_CONFIG = 92;
    private static final int SELECT_PROFILE = 43;
    private static final int IMPORT_PROFILE = 231;
    private static final int FILE_PICKER_RESULT_KITKAT = 392;
    private boolean mCmfixed = false;
    private static final int START_VPN_PROFILE = 70;
    private String mTransientAuthPW;
    private String mTransientCertOrPCKS12PW;
    private Button selectFie;
    private TextView status;
    private Switch aSwitch;
    private VpnProfile vpnProfile;
    private IOpenVPNServiceInternal mService;

    private boolean firstConnect = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vpn);
        ActionBar toolbar = getSupportActionBar();
        if (toolbar!=null) {
            toolbar.hide();
        }
        selectFie = findViewById(R.id.select_file);
        status = findViewById(R.id.status);
        aSwitch = findViewById(R.id.switch1);
        boolean vpnActive = VpnStatus.isVPNActive();
//        if (vpnActive) {
//            aSwitch.setChecked(true);
//        }
        selectFie.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startImportConfigFilePicker();
            }
        });

        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (vpnProfile != null && vpnProfile.mConnections.length > 0) {
                    vpnProfile.mConnections[0].mUseUdp = false;
                    if (isChecked) {
                        status.setText("状态：连接中.......");
                        startOrStopVPN(vpnProfile);
                    } else {
                        status.setText("状态：已断开");
//
//                        Intent intent = new Intent(VpnActivity.this, OpenVPNService.class);
//                        intent.setAction(OpenVPNService.START_SERVICE);
//                        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

                        ProfileManager.setConntectedVpnProfileDisconnected(VpnActivity.this);
                        if (mService != null) {
                            try {
                                mService.stopVPN(false);
                            } catch (RemoteException e) {
                                VpnStatus.logException(e);
                            }
                        }
                    }
                } else {
                    Toast.makeText(VpnActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                }
            }
        });

        VpnStatus.addStateListener(this);
    }


    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {

            mService = IOpenVPNServiceInternal.Stub.asInterface(binder);
            IServiceStatus service = IServiceStatus.Stub.asInterface(binder);
            try {
                if (mTransientAuthPW != null)
                    service.setCachedPassword(vpnProfile.getUUIDString(), PasswordCache.AUTHPASSWORD, mTransientAuthPW);
                if (mTransientCertOrPCKS12PW != null)
                    service.setCachedPassword(vpnProfile.getUUIDString(), PasswordCache.PCKS12ORCERTPASSWORD, mTransientCertOrPCKS12PW);

                onActivityResult(START_VPN_PROFILE, Activity.RESULT_OK, null);

            } catch (RemoteException e) {
                e.printStackTrace();
            }

            unbindService(this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }
    };


    private boolean startImportConfigFilePicker() {
        boolean startOldFileDialog = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && !Utils.alwaysUseOldFileChooser(this))
            startOldFileDialog = !startFilePicker();

        if (startOldFileDialog)
            startImportConfig();

        return true;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private boolean startFilePicker() {

        Intent i = Utils.getFilePickerIntent(this, Utils.FileType.OVPN_CONFIG);
        if (i != null) {
            startActivityForResult(i, FILE_PICKER_RESULT_KITKAT);
            return true;
        } else
            return false;
    }

    private void startImportConfig() {
        Intent intent = new Intent(this, FileSelect.class);
        intent.putExtra(FileSelect.NO_INLINE_SELECTION, true);
        intent.putExtra(FileSelect.WINDOW_TITLE, R.string.import_configuration_file);
        startActivityForResult(intent, SELECT_PROFILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK)
            return;
        if (requestCode == SELECT_PROFILE) {
            String fileData = data.getStringExtra(FileSelect.RESULT_DATA);
            Uri uri = new Uri.Builder().path(fileData).scheme("file").build();

            startConfigImport(uri);
        } else if (requestCode == IMPORT_PROFILE) {
            String profileUUID = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID);
            vpnProfile = ProfileManager.get(this, profileUUID);
            selectFie.setText("证书(已选择)");
        } else if (requestCode == FILE_PICKER_RESULT_KITKAT) {
            if (data != null) {
                Uri uri = data.getData();
                startConfigImport(uri);
            }
        } else if (requestCode == START_VPN_PROFILE) {
            if (resultCode == Activity.RESULT_OK) {
                int needpw = vpnProfile.needUserPWInput(mTransientCertOrPCKS12PW, mTransientAuthPW);
                if (needpw != 0) {
                    VpnStatus.updateStateString("USER_VPN_PASSWORD", "", R.string.state_user_vpn_password,
                            ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT);
//                    askForPW(needpw);
                } else {
                    SharedPreferences prefs = Preferences.getDefaultSharedPreferences(this);
                    boolean showLogWindow = prefs.getBoolean("showlogwindow", true);

//                    if (!mhideLog && showLogWindow)
//                        showLogWindow();
                    ProfileManager.updateLRU(this, vpnProfile);
                    VPNLaunchHelper.startOpenVpn(vpnProfile, getBaseContext());

                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // User does not want us to start, so we just vanish
                VpnStatus.updateStateString("USER_VPN_PERMISSION_CANCELLED", "", R.string.state_user_vpn_permission_cancelled,
                        ConnectionStatus.LEVEL_NOTCONNECTED);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    VpnStatus.logError(R.string.nought_alwayson_warning);

                finish();
            }
        }

    }

    private void startConfigImport(Uri uri) {
        Intent startImport = new Intent(this, ConfigConverter.class);
        startImport.setAction(ConfigConverter.IMPORT_PROFILE);
        startImport.setData(uri);
        startActivityForResult(startImport, IMPORT_PROFILE);
    }

    private void startOrStopVPN(VpnProfile profile) {
        if (VpnStatus.isVPNActive() && profile.getUUIDString().equals(VpnStatus.getLastConnectedVPNProfile())) {
            status.setText("已断开");
            ProfileManager.setConntectedVpnProfileDisconnected(VpnActivity.this);
            if (mService != null) {
                try {
                    mService.stopVPN(false);
                } catch (RemoteException e) {
                    VpnStatus.logException(e);
                }
            }
        } else {
            startVPN(profile);
        }
    }

    private void startVPN(VpnProfile profile) {
        getPM().saveProfile(this, profile);

        // Check if we need to clear the log
        if (Preferences.getDefaultSharedPreferences(this).getBoolean(CLEARLOG, true))
            VpnStatus.clearLog();
        if (vpnProfile == null) {
            VpnStatus.logError(R.string.shortcut_profile_notfound);
        } else {
            launchVPN();
        }
    }

    private ProfileManager getPM() {
        return ProfileManager.getInstance(this);
    }

    void launchVPN() {
        int vpnok = vpnProfile.checkProfile(this);
        if (vpnok != R.string.no_error_found) {
            selectFie.setText("报错了");
            return;
        }

        Intent intent = VpnService.prepare(this);
        // Check if we want to fix /dev/tun
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(this);
        boolean usecm9fix = prefs.getBoolean("useCM9Fix", false);
        boolean loadTunModule = prefs.getBoolean("loadTunModule", false);

        if (loadTunModule)
            execeuteSUcmd("insmod /system/lib/modules/tun.ko");

        if (usecm9fix && !mCmfixed) {
            execeuteSUcmd("chown system /dev/tun");
        }

        if (intent != null) {
            VpnStatus.updateStateString("USER_VPN_PERMISSION", "", R.string.state_user_vpn_permission,
                    ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT);
            // Start the query
            try {
                startActivityForResult(intent, START_VPN_PROFILE);
            } catch (ActivityNotFoundException ane) {
                // Shame on you Sony! At least one user reported that
                // an official Sony Xperia Arc S image triggers this exception
                VpnStatus.logError(R.string.no_vpn_support_image);
                selectFie.setText("又报错了");
            }
        } else {
            onActivityResult(START_VPN_PROFILE, Activity.RESULT_OK, null);
        }

    }

    private void execeuteSUcmd(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("su", "-c", command);
            Process p = pb.start();
            int ret = p.waitFor();
            if (ret == 0)
                mCmfixed = true;
        } catch (InterruptedException | IOException e) {
            VpnStatus.logException("SU command", e);
        }
    }


//    private void askForPW(final int type) {
//
//        final EditText entry = new EditText(this);
//
//        entry.setSingleLine();
//        entry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
//        entry.setTransformationMethod(new PasswordTransformationMethod());
//
//        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
//        dialog.setTitle(getString(R.string.pw_request_dialog_title, getString(type)));
//        dialog.setMessage(getString(R.string.pw_request_dialog_prompt, vpnProfile.mName));
//
//
//        @SuppressLint("InflateParams") final View userpwlayout = getLayoutInflater().inflate(R.layout.userpass, null, false);
//
//        if (type == R.string.password) {
//            ((EditText) userpwlayout.findViewById(R.id.username)).setText(vpnProfile.mUsername);
//            ((EditText) userpwlayout.findViewById(R.id.password)).setText(vpnProfile.mPassword);
//            ((CheckBox) userpwlayout.findViewById(R.id.save_password)).setChecked(!TextUtils.isEmpty(vpnProfile.mPassword));
//            ((CheckBox) userpwlayout.findViewById(R.id.show_password)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//                @Override
//                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//                    if (isChecked)
//                        ((EditText) userpwlayout.findViewById(R.id.password)).setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
//                    else
//                        ((EditText) userpwlayout.findViewById(R.id.password)).setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
//                }
//            });
//
//            dialog.setView(userpwlayout);
//        } else {
//            dialog.setView(entry);
//        }

//        AlertDialog.Builder builder = dialog.setPositiveButton(android.R.string.ok,
//                new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//
//                        if (type == R.string.password) {
//                            vpnProfile.mUsername = ((EditText) userpwlayout.findViewById(R.id.username)).getText().toString();
//
//                            String pw = ((EditText) userpwlayout.findViewById(R.id.password)).getText().toString();
//                            if (((CheckBox) userpwlayout.findViewById(R.id.save_password)).isChecked()) {
//                                vpnProfile.mPassword = pw;
//                            } else {
//                                vpnProfile.mPassword = null;
//                                mTransientAuthPW = pw;
//                            }
//                        } else {
//                            mTransientCertOrPCKS12PW = entry.getText().toString();
//                        }
//                        Intent intent = new Intent(VpnActivity.this, OpenVPNStatusService.class);
//                        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
//                    }
//
//                });
//        dialog.setNegativeButton(android.R.string.cancel,
//                new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        VpnStatus.updateStateString("USER_VPN_PASSWORD_CANCELLED", "", R.string.state_user_vpn_password_cancelled,
//                                ConnectionStatus.LEVEL_NOTCONNECTED);
//                        finish();
//                    }
//                });
//
//        dialog.create().show();

//    }

    @Override
    public void updateState(String state, String logmessage, int localizedResId, ConnectionStatus level) {
        Log.d("ljg", "updateState: " + state + "logmessage:" + logmessage + "localzeresid:" + localizedResId + "level:" + level);
        if (state.equals("CONNECTED")) {
//            if (firstConnect) {
//                Intent intent = new Intent(VpnActivity.this, OpenVPNService.class);
//                intent.setAction(OpenVPNService.START_SERVICE);
//                bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
//                firstConnect = false;
//            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    status.setText("状态：连接成功！");
                }
            });
        }
    }

    @Override
    public void setConnectedVPN(String uuid) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        unbindService(mConnection);
    }

}
