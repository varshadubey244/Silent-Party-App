package com.maxdota.wifiparty;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.maxdota.maxhelper.MaxHelper;
import com.maxdota.maxhelper.base.BaseActivity;
import com.maxdota.maxhelper.base.BaseApplication;
import com.maxdota.maxhelper.base.BaseFragment;
import com.maxdota.maxhelper.model.MaxAudioData;
import com.maxdota.wifiparty.adhoc.ChatAcceptThread;
import com.maxdota.wifiparty.adhoc.ChatSocketThread;
import com.maxdota.wifiparty.adhoc.SongAcceptThread;
import com.maxdota.wifiparty.adhoc.SongSocketThread;
import com.maxdota.wifiparty.adhoc.WiFiDirectBroadcastReceiver;
import com.maxdota.wifiparty.fragment.ChatFragment;
import com.maxdota.wifiparty.fragment.InfoFragment;
import com.maxdota.wifiparty.fragment.PartyPagerAdapter;
import com.maxdota.wifiparty.fragment.PlaylistFragment;
import com.maxdota.wifiparty.fragment.PrivateChatFragment;
import com.maxdota.wifiparty.model.ChatData;
import com.maxdota.wifiparty.model.LikeData;
import com.maxdota.wifiparty.model.MessageData;
import com.maxdota.wifiparty.model.NameListData;
import com.maxdota.wifiparty.model.PlaylistData;
import com.maxdota.wifiparty.model.PrivateChatData;
import com.maxdota.wifiparty.model.ReceivingSong;
import com.maxdota.wifiparty.model.SimpleMessage;
import com.maxdota.wifiparty.model.SongData;
import com.maxdota.wifiparty.model.SongPlayData;
import com.maxdota.wifiparty.model.SongRemoveData;
import com.maxdota.wifiparty.view.AddSongDialog;
import com.maxdota.wifiparty.view.PeerListAdapter;
import com.maxdota.wifiparty.view.RecyclerViewOnItemClickListener;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends BaseActivity implements WifiP2pManager.PeerListListener,
        WifiP2pManager.ConnectionInfoListener, AddSongDialog.AddSongDialogListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, SensorEventListener {
    public static final String ENCODING = "UTF-8";
    public static final int BUFFER_SIZE = 7 * 1024;
    public static final int CHAT_SOCKET_PORT = 8885;
    public static final int SONG_SOCKET_PORT = 8865;
    private static final int CONNECTION_TIMEOUT = 60000;

    private static final int REQUEST_CODE_AUDIO_PICKER = 101;
    private static final int REQUEST_CODE_READ_EXTERNAL_STORAGE_PERMISSION = 102;
    private static final int APP_STATUS_ADD_AUDIO = 1;
    private static final float SHAKE_THRESHOLD = 5;
    private static final int DANCING_TIME_INTERVAL = 7000;
    private static final int SENSOR_TIME_INTERVAL = 100;
    private static final int DANCING_CHECK_NUMBER = 6;

    private String mWifiNamePrefix;
    private String mWifiNameDivider;
    private int mNextSongId;
    private boolean mIsUserLeave;
    private boolean mIsDestroyed;
    private int mAppStatus;
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;
    boolean isWifiP2pEnabled;
    private ArrayList<WifiP2pDevice> mPeers;
    private FloatingActionButton mCreateFab;
    private View mMainContainer;
    private View mConnectionContainer;
    private RecyclerView mPeerList;
    private View mPartyContainer;
    private View mEmptyText;
    private View mOutBackground;
    private View mInBackground;
    private PeerListAdapter mPeerListAdapter;

    private boolean mIsInParty;
    private boolean mIsHost;
    private String mHostAddress;
    private final Object mChatSocketsLock = new Object();
    private ArrayList<ChatSocketThread> mChatSocketThreads;
    private Handler mHandler;
    private ChatSocketThread mHostChatSocketThread;

    private boolean mIsRegisteringReceiver;

    private ChatFragment mChatFragment;
    private PlaylistFragment mPlaylistFragment;
    private InfoFragment mInfoFragment;
    private PartyPagerAdapter mPartyPagerAdapter;
    private AddSongDialog mAddSongDialog;
    private MaxAudioData mTempAudioData;
    private MediaPlayer mMediaPlayer;

    private final Object mQueuingAddSongsLock = new Object();
    private ArrayList<SongData> mPendingSongs = new ArrayList<>();
    private ArrayList<ReceivingSong> mQueueingAddedSongs = new ArrayList<>();
    private HashMap<Long, SongData> mSongs;
    private ArrayList<Long> mPlaylist;
    private ReceivingSong mReceivingSong;
    private SongAcceptThread mSongAcceptThread;

    private long mTimeDifferenceWithHost;
    private ChatAcceptThread mChatAcceptThread;
    private PlaylistData mPlaylistData;

    private Runnable mConnectionTimeoutCallback;
    public String mDeviceName;
    private SongSocketThread.SongSocketListener mSongSocketListener;

    private final Object mUserNameLock = new Object();
    private ArrayList<String> mUserNames;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private long mLastSensorTime;
    private float mLastX;
    private float mLastY;
    private float mLastZ;
    private boolean mIsDancing;
    private int mMovingCheck;
    private long mMovingCheckAnchorTime;
    private MenuItem mIndicatorItem;
    private MenuItem mLeaveItem;
    private TabLayout mTabLayout;
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initData();
        initUi();
        initSensor();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mCreateFab = (FloatingActionButton) findViewById(R.id.create_fab);
        mCreateFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name;
                if (mDeviceName == null) {
                    name = "Unknown";
                } else {
                    name = mDeviceName;
                }
                mMaxHelper.showInputDialog(MainActivity.this, "Create Room", "Enter room name", name,
                        new MaxHelper.InputDialog() {
                            @Override
                            public void onInput(final String text) {
                                String wifiName = getWifiName(text);
                                log("Wifi name: " + wifiName);
                                updateWifiName(wifiName, new WifiP2pManager.ActionListener() {
                                    @Override
                                    public void onSuccess() {
                                        mDeviceName = text;
                                        createParty();
                                    }

                                    @Override
                                    public void onFailure(int reason) {
                                        toast("Update name fails. Is it too long?");
                                    }
                                });
                            }

                            @Override
                            public void onCancel() {
                            }
                        });
            }
        });

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
            }
        });
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    private void initSensor() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    private void initData() {
        if (MainApplication.IS_DEBUGGING) {
            mMaxHelper.setLogLevel(MaxHelper.LogLevel.E);
        }
        mPrivateChatFragments = new ArrayList<>();
        mWifiNamePrefix = getString(R.string.wifi_name_prefix);
        mWifiNameDivider = getString(R.string.wifi_name_divider);
        mNextSongId = 1;
        mPeers = new ArrayList<>();
        mHandler = new Handler();
        mChatSocketThreads = new ArrayList<>();
        mChatFragment = new ChatFragment();
        mPlaylistFragment = new PlaylistFragment();
        mInfoFragment = new InfoFragment();
        mSongs = mPlaylistFragment.getSongs();
        mPlaylist = mPlaylistFragment.getPlaylist();
        mPlaylistData = new PlaylistData(mPlaylist);
        mUserNames = new ArrayList<>();
        mFragments = new ArrayList<>();
        mFragments.add(mPlaylistFragment);
        mFragments.add(mChatFragment);
        mFragments.add(mInfoFragment);

        mUpdateSongProgressRunnable = new Runnable() {
            @Override
            public void run() {
                log("test 1: " + mMediaPlayer);
                if (mMediaPlayer == null || mMediaPlayer != mCurrentTrackingProgress
                        || !mMediaPlayer.isPlaying()) {
                    log("test return");
                    return;
                }
                mPlaylistFragment.updateSongProgress(mMediaPlayer.getCurrentPosition());
                scheduleUpdateSongProgress();
                log("schedule next");
            }
        };
    }

    private ArrayList<BaseFragment> mFragments;

    private void initUi() {
        mCircleLoader = findViewById(R.id.circle_loader);
        mInBackground = findViewById(R.id.in_background);
        mOutBackground = findViewById(R.id.out_background);
        mPartyPagerAdapter = new PartyPagerAdapter(getSupportFragmentManager(), mFragments);
        mViewPager = (ViewPager) findViewById(R.id.party_pager);
        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mViewPager.setAdapter(mPartyPagerAdapter);
        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));
        mTabLayout.setupWithViewPager(mViewPager);

        mConnectionContainer = findViewById(R.id.connection_container);
        mEmptyText = findViewById(R.id.empty_text);
        mPartyContainer = findViewById(R.id.party_container);
        mPeerList = (RecyclerView) findViewById(R.id.peer_list);
        mMainContainer = findViewById(R.id.main_container);

        mPeerListAdapter = new PeerListAdapter(mPeers, new RecyclerViewOnItemClickListener() {
            @Override
            public void onItemClicked(int position) {
                connect(mPeers.get(position));
            }
        });
        mPeerList.setLayoutManager(new LinearLayoutManager(this));
        mPeerList.setAdapter(mPeerListAdapter);
    }

    private void goToPartyScreen(String hostAddress) {
        log("go to party screen");

        // current user is the host
        if (hostAddress == null) {
            openChatSocketAndWait();
            mIsHost = true;
            addUserName(mDeviceName);
        } else {
            mHostAddress = hostAddress;
            connectToChatSocket();
            mIsHost = false;
        }

        showParty();
    }

    // return false if duplicated
    private boolean addUserName(String userName) {
        synchronized (mUserNameLock) {
            if (mUserNames.contains(userName)) {
                return false;
            }
            mUserNames.add(userName);
            mInfoFragment.addUser(userName);
            return true;
        }
    }

    public void discoverPeer() {
        log("try to discover peer");
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(int reasonCode) {
                toast("Cannot see surrounding parties. Error " + reasonCode);
            }
        });
    }

    private void connect(WifiP2pDevice device) {
        log("try to connect");
        showCircleLoader();
        addConnectionTimeout();
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                toast("Connecting to the party");
            }

            @Override
            public void onFailure(int reason) {
                hideCircleLoader();
                toast("Connection failed. Error " + reason);
            }
        });
    }

    public void createParty() {
        log("create party");
        mManager.createGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                toast("Create party success.");
                goToPartyScreen(null);
            }

            @Override
            public void onFailure(int reason) {
                log("P2P group creation failed. Error " + reason);
            }
        });
    }

    public void removeParty() {
        log("remove party");
        mIsUserLeave = true;
        mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                toast("Leave party success.");
            }

            @Override
            public void onFailure(int reason) {
                log("P2P removal failed. Error " + reason);
            }
        });
        hideParty();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPeers.clear();
        if (!mIsInParty) {
            toggleWifiReceiver(true);
        }
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        toggleWifiReceiver(false);
        mSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        log("onDestroy");
        mIsDestroyed = true;
        if (mIsInParty) {
            clearConnections();

            if (mManager != null) {
                mManager.removeGroup(mChannel, null);
            }
        }
        super.onDestroy();
    }

    private void clearConnections() {
        if (mChatAcceptThread != null) {
            mChatAcceptThread.closeSocket();
            mChatAcceptThread = null;
            if (mChatSocketThreads != null) {
                synchronized (mChatSocketsLock) {
                    for (ChatSocketThread socketThread : mChatSocketThreads) {
                        socketThread.closeSocket();
                    }
                    mChatSocketThreads.clear();
                }
            }
        }

        if (mHostChatSocketThread != null) {
            mHostChatSocketThread.write(new SimpleMessage(MessageData.MESSAGE_TYPE_LEAVE).toString());
            mHostChatSocketThread.closeSocket();
            mHostChatSocketThread = null;
        }

        if (mSongAcceptThread != null) {
            mSongAcceptThread.closeSocket();
            mSongAcceptThread = null;
        }
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        Collection<WifiP2pDevice> refreshedPeers = peers.getDeviceList();
        log("peers available: " + refreshedPeers.size());
        if (!refreshedPeers.equals(mPeers)) {
            mPeers.clear();
            for (WifiP2pDevice peer : refreshedPeers) {
                log("peer name: " + peer.deviceName);
                String name = getNameFromWifiName(peer.deviceName);
                if (name != null) {
                    peer.deviceName = name;
                    mPeers.add(peer);
                }
            }
            mPeerListAdapter.notifyDataSetChanged();
        }

        if (mPeers.size() == 0) {
            mEmptyText.setVisibility(View.VISIBLE);
            mPeerList.setVisibility(View.GONE);
        } else {
            mEmptyText.setVisibility(View.GONE);
            mPeerList.setVisibility(View.VISIBLE);
        }
    }

    public void setWifiP2pEnabled(boolean wifiP2pEnabled) {
        isWifiP2pEnabled = wifiP2pEnabled;
        if (wifiP2pEnabled) {
            log("Wifi P2P is enabled");
            discoverPeer();
        } else {
            toast("Please enable Wifi and try again");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        mIndicatorItem = menu.getItem(0);
        mLeaveItem = menu.getItem(1);
        mLeaveItem.setVisible(mIsInParty);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_leave_party) {
            removeParty();
            return true;
        } else if (id == R.id.action_indicator) {
            mMaxHelper.showExplanationDialog(this, "Let's dance!");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void toast(final int resId) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, resId, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void toast(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateWifiName(String newName, WifiP2pManager.ActionListener listener) {
        try {
            log("Updating name: " + newName);
            Class[] paramTypes = new Class[3];
            paramTypes[0] = WifiP2pManager.Channel.class;
            paramTypes[1] = String.class;
            paramTypes[2] = WifiP2pManager.ActionListener.class;
            Method setDeviceName = mManager.getClass().getMethod("setDeviceName", paramTypes);
            setDeviceName.setAccessible(true);

            Object arglist[] = new Object[3];
            arglist[0] = mChannel;
            arglist[1] = newName;
            arglist[2] = listener;
            setDeviceName.invoke(mManager, arglist);
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        log("onConnectionInfoAvailable");
        if (!mIsInParty) {
            if (info.isGroupOwner) {
                String name = getNameFromWifiName(mDeviceName);
                if (name == null) {
                    updateWifiName(getWifiName(mDeviceName), null);
                }
                goToPartyScreen(null);
            } else {
                if (info.groupOwnerAddress != null) {
                    goToPartyScreen(info.groupOwnerAddress.getHostAddress());
                }
            }
        }
    }

    public void requestConnectionInfo(Intent intent) {
        if (mManager == null) {
            return;
        }

        log("requestConnectionInfo");
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
        if (networkInfo.isConnected()) {
            mManager.requestConnectionInfo(mChannel, this);
        }
    }

    private void addConnectionTimeout() {
        mConnectionTimeoutCallback = new Runnable() {
            @Override
            public void run() {
                if (!mIsInParty) {
                    mManager.removeGroup(mChannel, null);
                    hideCircleLoader();
                    mMaxHelper.showErrorDialog(MainActivity.this, "You are not authorised to join this party");
                }
            }
        };
        mHandler.postDelayed(mConnectionTimeoutCallback, CONNECTION_TIMEOUT);
    }

    private void connectToChatSocket() {
        log("connectToChatSocket");
        mHostChatSocketThread = new ChatSocketThread(mHostAddress);
        mHostChatSocketThread.setOnChangesListener(new ChatSocketThread.OnChangesListener() {
            @Override
            public void onConnectionEstablished() {
                showInputUserNameDialog();
            }

            @Override
            public void onDataReceived(String data) {
                processData(mHostChatSocketThread, data);
            }

            @Override
            public void onConnectionLost() {
                log("onConnectionLost");
                resetPlaylistAndShowReconnectDialog();
            }
        });
        mHostChatSocketThread.start();
        hideCircleLoader();
    }

    private void resetPlaylistAndShowReconnectDialog() {
        if (mIsDestroyed || mIsUserLeave) {
            return;
        }
        resetMediaPlayer();
        mPlaylistFragment.reset();
        mMaxHelper.showYesNoDialog(this, "Cannot connect to the host", "Try Again",
                "Leave", new MaxHelper.YesNoDialog() {
                    @Override
                    public void onSelected(boolean isYes) {
                        if (isYes) {
                            showCircleLoader();
                            connectToChatSocket();
                        } else {
                            removeParty();
                        }
                    }
                });
    }

    private void openChatSocketAndWait() {
        log("openChatSocketAndWait");
        mChatAcceptThread = new ChatAcceptThread(new ChatAcceptThread.ConnectionListener() {
            @Override
            public void onNewConnection(Socket socket) {
                final ChatSocketThread chatSocketThread = new ChatSocketThread(socket);
                chatSocketThread.setOnChangesListener(new ChatSocketThread.OnChangesListener() {
                    @Override
                    public void onConnectionEstablished() {
                        synchronized (mChatSocketsLock) {
                            mChatSocketThreads.add(chatSocketThread);
                        }
                        chatSocketThread.write(new SimpleMessage(MessageData.MESSAGE_TYPE_INIT_TIME,
                                System.currentTimeMillis()).toString());

                        chatSocketThread.write(mPlaylistData.toString());
                        for (SongData song : mSongs.values()) {
                            chatSocketThread.write(song.toString());
                        }
                    }

                    @Override
                    public void onDataReceived(String data) {
                        processData(chatSocketThread, data);
                    }

                    @Override
                    public void onConnectionLost() {
                        log("onConnectionLost");
                        if (mSongAcceptThread != null && chatSocketThread.getIpAddress().equals(mSongAcceptThread.mIpAddress)) {
                            mSongAcceptThread.closeSocket();
                            mReceivingSong = null;
                        }
                        synchronized (mChatSocketsLock) {
                            mChatSocketThreads.remove(chatSocketThread);
                            sendMessageDataToAll(new SimpleMessage(MessageData.MESSAGE_TYPE_REMOVE_NAME,
                                    chatSocketThread.mName));
                        }
                        Log.e("remove user", "user: " + chatSocketThread.mName);
                        synchronized (mUserNameLock) {
                            mUserNames.remove(chatSocketThread.mName);
                        }
                        mInfoFragment.removeUser(chatSocketThread.mName);
                    }
                });
                chatSocketThread.start();
            }
        });
        mChatAcceptThread.start();
    }

    private void processData(ChatSocketThread socketThread, String rawData) {
        MessageData messageData = MessageData.parseData(rawData);
        if (messageData == null) {
            return;
        }

        if (messageData instanceof ChatData) {
            updateChatText((ChatData) messageData);
            updateMessageToOthers(socketThread, rawData);
        } else if (messageData instanceof SongData) {
            final SongData songData = (SongData) messageData;
            switch (songData.getMessageType()) {
                case MessageData.MESSAGE_TYPE_ADD_SONG:
                    if (checkSongExistence(socketThread, songData)) {
                        if (!mIsHost && mMediaPlayer == null) {
                            mHostChatSocketThread.write(new SimpleMessage(MessageData.MESSAGE_TYPE_REQUEST_CURRENT_PLAYING).toString());
                        }
                        return;
                    }

                    if (mIsHost) {
                        // update song id
                        songData.mId = mNextSongId++;
                        mPlaylist.add(songData.mId);
                        socketThread.write(mPlaylistData.toString());
                        socketThread.write(new SongData(MessageData.MESSAGE_TYPE_UPDATE_SONG_ID, songData).toString());
                    }

                    // add an empty song holder
                    mPlaylistFragment.addSong(songData);
                    synchronized (mQueuingAddSongsLock) {
                        mQueueingAddedSongs.add(new ReceivingSong(socketThread.getIpAddress(),
                                songData));
                        checkToReceiveNextSong(socketThread);
                    }
                    break;
                case MessageData.MESSAGE_TYPE_READY_RECEIVE_SONG:
                    log("request songId: " + songData.mId);
                    SongData requestSong = mSongs.get(songData.mId);

                    if (requestSong == null || requestSong.mPath == null) {
                        toast("Song is no longer valid");
                        socketThread.write(new SimpleMessage(MessageData.MESSAGE_TYPE_CLOSE_SONG_SOCKET).toString());
                    } else {
                        if (mSongSocketListener == null) {
                            mSongSocketListener = new SongSocketThread.SongSocketListener() {
                                @Override
                                public void onError(final String ipAddress, final String filePath) {
                                    mHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            new SongSocketThread(ipAddress, filePath, mSongSocketListener).start();
                                        }
                                    }, 2000);
                                }
                            };
                        }

                        new SongSocketThread(socketThread.getIpAddress(),
                                requestSong.mPath, mSongSocketListener).start();
                    }
                    break;
                case MessageData.MESSAGE_TYPE_UPDATE_SONG_ID:
                    final SongData song = SongData.findExactSongWithoutId(mPendingSongs, songData);
                    if (song == null) {
                        log("Cannot find song to update id");
                    } else {
                        song.mId = songData.mId;
                        mSongs.put(song.mId, song);
                        log("update songId: " + songData.mId);
                        mPlaylistFragment.refresh();
                    }
                    break;
            }
        } else if (messageData instanceof SimpleMessage) {
            switch (messageData.getMessageType()) {
                case MessageData.MESSAGE_TYPE_KICK:
                    toast("Unfortunately, you are kicked out of the party");
                    removeParty();
                    break;
                case MessageData.MESSAGE_TYPE_CLOSE_SONG_SOCKET:
                    toast("Song is no longer valid");
                    mSongAcceptThread.closeSocket();
                    break;
                case MessageData.MESSAGE_TYPE_REQUEST_CURRENT_PLAYING:
                    SongData songData = mPlaylistFragment.getSelectedSong();
                    if (songData != null && mMediaPlayer != null) {
                        socketThread.write(new SongPlayData(MessageData.MESSAGE_TYPE_CURRENT_PLAYING_SONG,
                                songData, mMediaPlayer.isPlaying(),
                                System.currentTimeMillis() - mMediaPlayer.getCurrentPosition()).toString());
                    }
                    break;
                case MessageData.MESSAGE_TYPE_INIT_TIME:
                    SimpleMessage simpleMessage = (SimpleMessage) messageData;
                    mTimeDifferenceWithHost = simpleMessage.mExtraLong - System.currentTimeMillis();
                    break;
                case MessageData.MESSAGE_TYPE_PAUSE_SONG:
                    if (mMediaPlayer != null) {
                        mMediaPlayer.pause();
                    }
                    break;
                case MessageData.MESSAGE_TYPE_RESUME_SONG:
                    if (mMediaPlayer != null) {
                        mMediaPlayer.start();
                        scheduleUpdateSongProgress();
                    }
                    break;
                case MessageData.MESSAGE_TYPE_INIT_NAME:
                    SimpleMessage nameMessage = (SimpleMessage) messageData;
                    if (mIsHost) {
                        if (addUserName(nameMessage.mExtraString)) {
                            updateMessageToOthers(socketThread, rawData);
                            socketThread.write(new NameListData(mUserNames).toString());
                            socketThread.mName = nameMessage.mExtraString;
                        } else {
                            socketThread.write(new SimpleMessage(MessageData.MESSAGE_TYPE_DUPLICATE_NAME).toString());
                        }
                    } else {
                        addUserName(nameMessage.mExtraString);
                    }
                    break;
                case MessageData.MESSAGE_TYPE_DUPLICATE_NAME:
                    showInputUserNameDialog();
                    hideCircleLoader();
                    mMaxHelper.showErrorDialog(MainActivity.this, "Name already existed. Please choose another.");
                    break;
                case MessageData.MESSAGE_TYPE_LEAVE:
                    log("closing left user socket");
                    socketThread.closeSocket();
                    break;
                case MessageData.MESSAGE_TYPE_REMOVE_NAME:
                    SimpleMessage removeMessage = (SimpleMessage) messageData;
                    synchronized (mUserNames) {
                        mUserNames.remove(removeMessage.mExtraString);
                    }
                    mInfoFragment.removeUser(removeMessage.mExtraString);
                    break;
                case MessageData.MESSAGE_TYPE_REARRANGE_PLAYLIST:
                    SimpleMessage rearrangeMessage = (SimpleMessage) messageData;
                    rearrangePlaylist(rearrangeMessage.mExtraInt, rearrangeMessage.mExtraBoolean);
                    break;
            }
        } else if (messageData instanceof SongPlayData) {
            log("Update current playing song");
            mHandler.removeCallbacks(mUpdateSongProgressRunnable);
            SongPlayData songPlayData = (SongPlayData) messageData;
            if (songPlayData.mIsPlaying) {
            }
            mPlaylistFragment.playSong(songPlayData);
        } else if (messageData instanceof PlaylistData) {
            PlaylistData playlistData = (PlaylistData) messageData;
            mPlaylist.clear();
            mPlaylist.addAll(playlistData.mPlaylist);
        } else if (messageData instanceof SongRemoveData) {
            SongRemoveData songRemoveData = (SongRemoveData) messageData;
            if (mPlaylist.get(songRemoveData.mIndex) == songRemoveData.mId) {
                mPlaylistFragment.removeSong(songRemoveData.mId, songRemoveData.mIndex);
            }
        } else if (messageData instanceof NameListData) {
            NameListData nameListData = (NameListData) messageData;
            mUserNames.clear();
            mUserNames.addAll(nameListData.mNames);
            mInfoFragment.setUserNames(mUserNames);
            Log.e("name", mUserNames.toString());
            hideCircleLoader();
        } else if (messageData instanceof LikeData) {
            LikeData likeData = (LikeData) messageData;
            SongData songData = mSongs.get(likeData.getSongId());
            songData.updateLike(likeData.isLike());
            mPlaylistFragment.notifyAdapterChanges();
            if (mIsHost) {
                sendMessageDataToAll(likeData);
            }
        } else if (messageData instanceof PrivateChatData) {
            PrivateChatData privateChatData = (PrivateChatData) messageData;
            if (privateChatData.mTarget.equals(mDeviceName)) {
                int fragmentIndex = findChatTabByName(privateChatData.mOwner);
                PrivateChatFragment fragment;
                if (fragmentIndex == -1) {
                    fragment = addTab(privateChatData.mOwner, false);
                } else {
                    fragment = mPrivateChatFragments.get(fragmentIndex);
                }
                fragment.addChat(privateChatData.mOwner, privateChatData.mChat);
            } else {
                if (mIsHost) {
                    updateMessageToOthers(socketThread, rawData);
                }
            }
        }
    }

    private void showInputUserNameDialog() {
        mMaxHelper.showInputDialog(MainActivity.this, "Enter your name", "",
                mDeviceName, new MaxHelper.InputDialog() {
                    @Override
                    public void onInput(String text) {
                        showCircleLoader();
                        mHostChatSocketThread
                                .write(new SimpleMessage(MessageData.MESSAGE_TYPE_INIT_NAME,
                                        text).toString());
                        mDeviceName = text;
                        updateWifiName(text, null);
                    }

                    @Override
                    public void onCancel() {
                        removeParty();
                    }
                });
    }

    // return true if the song exists
    private boolean checkSongExistence(ChatSocketThread socketThread, final SongData songData) {
        File file = new File(String.format(Locale.US, "%s/%s", SongAcceptThread.getDirPath(),
                maxAudioFileName(songData.mTitle, songData.mArtist, songData.mExtension)));
        if (file.exists()) {
            log("song already existed, adding to the playlist");
            songData.mPath = file.getPath();
            if (mIsHost) {
                songData.mId = mNextSongId++;
                mPlaylist.add(songData.mId);
                sendMessageData(mPlaylistData);
                updateMessageToOthers(socketThread, songData.toString());
                socketThread.write(new SongData(MessageData.MESSAGE_TYPE_UPDATE_SONG_ID, songData).toString());
            }
            mPlaylistFragment.addSong(songData);
            return true;
        }
        return false;
    }

    private ChatSocketThread findChatSocketThread(String ipAddress) {
        synchronized (mChatSocketsLock) {
            for (ChatSocketThread socketThread : mChatSocketThreads) {
                if (socketThread.getIpAddress().equals(ipAddress)) {
                    return socketThread;
                }
            }
        }
        return null;
    }

    private ChatSocketThread findChatSocketThreadByName(String name) {
        synchronized (mChatSocketsLock) {
            for (ChatSocketThread socketThread : mChatSocketThreads) {
                if (socketThread.mName.equals(name)) {
                    return socketThread;
                }
            }
        }
        return null;
    }

    private void openSongSocketAndNotifySender(final ReceivingSong receivingSong) {
        log("waiting for song");
        mReceivingSong = receivingSong;

        final ChatSocketThread socketThread;
        if (mIsHost) {
            socketThread = findChatSocketThread(mReceivingSong.mIpAddress);
            if (socketThread == null) {
                return;
            }
        } else {
            socketThread = mHostChatSocketThread;
        }
        mSongAcceptThread = new SongAcceptThread(this, mReceivingSong.mSongData,
                socketThread.getIpAddress(),
                new SongAcceptThread.ConnectionListener() {
                    @Override
                    public void onFileReceived(final SongData song) {
                        log("Empty receiving song");
                        mReceivingSong = null;
                        checkToReceiveNextSong(socketThread);
                        if (mIsHost) {
                            updateMessageToOthers(socketThread, mPlaylistData.toString());
                            updateMessageToOthers(socketThread, song.toString());
                        }

                        mPlaylistFragment.refresh();
                    }

                    @Override
                    public void onSocketClosed(SongData song) {
                        if (mReceivingSong != null && mReceivingSong.mSongData.mId == song.mId) {
                            mReceivingSong = null;
                            checkToReceiveNextSong(socketThread);
                        }
                    }
                });
        mSongAcceptThread.start();

        // send confirm ready to receive song message
        socketThread.write(new SongData(MessageData.MESSAGE_TYPE_READY_RECEIVE_SONG,
                mReceivingSong.mSongData).toString());
    }

    private void checkToReceiveNextSong(ChatSocketThread socketThread) {
        synchronized (mQueuingAddSongsLock) {
            if (mQueueingAddedSongs.size() > 0) {
                log("Processing next song");
                ReceivingSong receivingSong = mQueueingAddedSongs.get(0);
                if (checkSongExistence(socketThread, receivingSong.mSongData)) {
                    mQueueingAddedSongs.remove(0);
                    checkToReceiveNextSong(socketThread);
                } else {
                    // only receive next song when there is no receiving in progress
                    if (mReceivingSong == null) {
                        mReceivingSong = receivingSong;
                        mQueueingAddedSongs.remove(0);
                        openSongSocketAndNotifySender(mReceivingSong);
                    }
                }
            } else {
                if (!mIsHost && mMediaPlayer == null) {
                    mHostChatSocketThread.write(new SimpleMessage(MessageData.MESSAGE_TYPE_REQUEST_CURRENT_PLAYING).toString());
                }
            }
        }
    }

    private void updateMessageToOthers(ChatSocketThread exception, String data) {
        synchronized (mChatSocketsLock) {
            for (ChatSocketThread socketThread : mChatSocketThreads) {
                if (socketThread != exception) {
                    socketThread.write(data + MessageData.MESSAGE_SUFFIX);
                }
            }
        }
    }

    private void showParty() {
        if (mConnectionTimeoutCallback != null) {
            mHandler.removeCallbacks(mConnectionTimeoutCallback);
            mConnectionTimeoutCallback = null;
        }
        hideCircleLoader();
        if (mLeaveItem != null) {
            mLeaveItem.setVisible(true);
        }
        mPlaylistFragment.tryToUpdateViews();
        mInfoFragment.tryToUpdateViews();
        mPartyContainer.setVisibility(View.VISIBLE);
        mConnectionContainer.setVisibility(View.GONE);
        mCreateFab.setVisibility(View.GONE);
        mOutBackground.setVisibility(View.GONE);
        mInBackground.setVisibility(View.VISIBLE);
        mIsUserLeave = false;
        mIsInParty = true;
        toggleWifiReceiver(false);
    }

    private void hideParty() {
        mReceivingSong = null;
        clearConnections();
        // reset device name to normal
        if (mIsHost) {
            if (mDeviceName != null) {
                updateWifiName(mDeviceName, null);
            }
        }
        deletePersistentGroups();
        synchronized (mUserNameLock) {
            mUserNames.clear();
        }
        mInfoFragment.clearUsers();

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mLeaveItem != null) {
                    mLeaveItem.setVisible(false);
                }
                resetMediaPlayer();
                mChatFragment.clearChat();
                synchronized (mPrivateChatFragmentsLock) {
                    for (int i = 0; i < mPrivateChatFragments.size(); i++) {
                        mPartyPagerAdapter.remove(i + 3);
                    }
                    mPrivateChatFragments.clear();
                }
                mViewPager.setCurrentItem(0);
                mPlaylistFragment.reset();
                mPartyContainer.setVisibility(View.GONE);
                mConnectionContainer.setVisibility(View.VISIBLE);
                mCreateFab.setVisibility(View.VISIBLE);
                mOutBackground.setVisibility(View.VISIBLE);
                mInBackground.setVisibility(View.GONE);
                mIsInParty = false;
                toggleWifiReceiver(true);
            }
        });
    }

    private void resetMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
            log("test remove call back");
            mHandler.removeCallbacks(mUpdateSongProgressRunnable);
        }
    }

    private void toggleWifiReceiver(boolean isRegister) {
        if (isRegister && !mIsRegisteringReceiver) {
            log("registerReceiver");
            registerReceiver(mReceiver, mIntentFilter);
            mIsRegisteringReceiver = true;
        } else if (!isRegister && mIsRegisteringReceiver) {
            log("unregisterReceiver");
            unregisterReceiver(mReceiver);
            mIsRegisteringReceiver = false;
        }
    }

    public void updateChatText(final ChatData data) {
        mChatFragment.addChat(data.getOwner(), data.getMessage());
    }

    public void sendChat(String message) {
        if (!mIsInParty || TextUtils.isEmpty(message)) {
            return;
        }

        ChatData chatData = new ChatData(message, 1, mDeviceName);
        sendMessageData(chatData);
        updateChatText(chatData);
    }

    private void sendMessageDataToAll(MessageData messageData) {
        synchronized (mChatSocketsLock) {
            for (ChatSocketThread socketThread : mChatSocketThreads) {
                socketThread.write(messageData.toString());
            }
        }
    }

    private void sendMessageData(MessageData messageData) {
        if (mIsHost) {
            sendMessageDataToAll(messageData);
        } else {
            sendDataToHost(messageData);
        }
    }

    private void sendDataToHost(MessageData messageData) {
        if (mHostChatSocketThread != null) {
            mHostChatSocketThread.write(messageData.toString());
        }
    }

    public void tryToAddSong() {
        if (mMaxHelper.checkAndRequestPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE,
                "The app needs to read your external storage to allow you to add song.\nPlease grant the permission.",
                REQUEST_CODE_READ_EXTERNAL_STORAGE_PERMISSION)) {
            addSong();
        } else {
            mAppStatus = APP_STATUS_ADD_AUDIO;
        }
    }

    private void addSong() {
        mMaxHelper.showAudioPicker(this, REQUEST_CODE_AUDIO_PICKER);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mAppStatus == APP_STATUS_ADD_AUDIO) {
            if (mMaxHelper.onRequestPermissionsResult(requestCode, grantResults,
                    REQUEST_CODE_READ_EXTERNAL_STORAGE_PERMISSION)) {
                addSong();
            } else {
                toast("Permission denied. Cannot add song");
            }
            mAppStatus = -1;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CODE_AUDIO_PICKER) {
                String path = mMaxHelper.getAudioPath(this, data.getData());
                if (path != null) {
                    try {
                        MaxAudioData audioData = mMaxHelper.retrieveAudioData(path);
                        if (mAddSongDialog == null) {
                            mAddSongDialog = new AddSongDialog(this, this);
                        }

                        updateAudioDataFromFileName(audioData, path);
                        mTempAudioData = audioData;
                        mAddSongDialog.display(audioData);
                    } catch (RuntimeException ex) {
                        toast("Invalid file data. Unable to read.");
                    }
                }
            }
        }
    }

    public String maxAudioFileName(String title, String artist, String extension) {
        return String.format("%s - %s%s", title, artist, extension);
    }

    // return new file
    public File renameFile(File file, String parentDir, String title, String artist, String extension,
                           String durationTime) {
        String newName = maxAudioFileName(title, artist, extension);
        String newPath = String.format(Locale.US, "%s/%s", parentDir, newName);
        File renamedFile = new File(newPath);
        file.renameTo(renamedFile);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DATA, newPath);
        values.put(MediaStore.Audio.Media.TITLE, title);
        values.put(MediaStore.Audio.Media.ARTIST, artist);
        values.put(MediaStore.Audio.Media.DURATION, durationTime);

        ContentResolver contentResolver = getContentResolver();
        int updatedRows = contentResolver.update(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values,
                MediaStore.Images.Media.DATA + "=?",
                new String[]{file.getPath()});

        if (updatedRows == 0) {
            values.put(MediaStore.Audio.Media.ALBUM, BaseApplication.sAppName);
            contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        }

        return renamedFile;
    }

    private void updateAudioDataFromFileName(MaxAudioData audioData, String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        int dividerIndex = fileName.indexOf(" - ");
        int extensionIndex = fileName.indexOf('.');
        if (dividerIndex != -1 && extensionIndex != -1 && dividerIndex < extensionIndex) {
            String title = fileName.substring(0, dividerIndex);
            String artist = fileName.substring(dividerIndex + 3, extensionIndex);
            audioData.setTitle(title);
            audioData.setArtist(artist);
        }
    }

    @Override
    public void onSongAdded(String songName, String artist) {
        File songFile = new File(mTempAudioData.getPath());
        String extension = SongData.getFileExtension(songFile.getName());

        if (!songName.equals(mTempAudioData.getTitle()) || !artist.equals(mTempAudioData.getArtist())) {
            mTempAudioData.setTitle(songName);
            mTempAudioData.setArtist(artist);
            songFile = renameFile(songFile, songFile.getParent(), songName, artist, extension,
                    mTempAudioData.getDurationTimeString());
            mTempAudioData.setPath(songFile.getPath());

            try {
                AudioFile f = AudioFileIO.read(songFile);
                Tag tag = f.getTag();
                if (tag == null) {
                    toast("Invalid media data. Cannot update song info.");
                } else {
                    tag.setField(FieldKey.TITLE, songName);
                    tag.setField(FieldKey.ARTIST, artist);
                    f.commit();
                }
            } catch (CannotReadException | TagException |
                    CannotWriteException | IOException | ReadOnlyFileException e) {
                log("read error: " + e.getMessage());
            } catch (InvalidAudioFrameException e) {
                toast("Invalid media data. Cannot update song info.");
            }
        }

        SongData song = new SongData(MessageData.MESSAGE_TYPE_ADD_SONG, mTempAudioData, extension);

        if (mIsHost) {
            song.mId = mNextSongId++;
            mPlaylistFragment.addNewSong(song);
            sendMessageData(mPlaylistData);
        } else {
            mPendingSongs.add(song);
        }
        sendMessageData(song);
    }

    public void onSongRemoved(long songId, int songIndex) {
        SongRemoveData songRemoveData = new SongRemoveData(songId, songIndex);
        sendMessageDataToAll(songRemoveData);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mMediaPlayer != null && mMediaPlayer == mp) {
            mPlaylistFragment.playNextSong();
        }
    }

    // return true if that song is played
    public boolean songSelected(SongData song) {
        if (!mIsHost) {
            return false;
        }
        if (!song.isReady()) {
            toast("Song is not ready yet");
            return false;
        }

        playSong(song);
        SongPlayData songPlayData = new SongPlayData(MessageData.MESSAGE_TYPE_CURRENT_PLAYING_SONG, song);
        sendMessageDataToAll(songPlayData);
        return true;
    }

    // return song current time progress
    public int playSong(SongData song, long startedAt) {
        if (song.mPath == null) {
            toast("Song is not ready yet");
            return -1;
        }

        final int startTime = startedAt == -1 ? 0 : (int) (getHostCurrentTime() - startedAt);

        MediaPlayer.OnPreparedListener completionListener = new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                if (mp == mMediaPlayer) {
                    log("on prepare complete " + mp);
                    mp.seekTo(startTime);
                    mp.start();
                    scheduleUpdateSongProgress();
                }
            }
        };
        mMediaPlayer = mMaxHelper.prepareAudio(song.mPath, completionListener, this, this);
        mCurrentTrackingProgress = mMediaPlayer;
        log("set current tracking: " + mCurrentTrackingProgress);

        if (mMediaPlayer == null) {
            mPlaylistFragment.handleSongError();
        }
        return startTime;
    }

    // startAt = -1 means playing the song from the beginning
    // return song start time
    public int prepareSong(SongData song, long startedAt) {
        if (song.mPath == null) {
            toast("Song is not ready yet");
            return -1;
        }

        final int startTime = (int) (getHostCurrentTime() - startedAt);
        mMediaPlayer = mMaxHelper.prepareAudio(song.mPath, new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                if (mp == mMediaPlayer) {
                    mp.seekTo(startTime);
                    mCurrentTrackingProgress = mMediaPlayer;
                    scheduleUpdateSongProgress();
                }
            }
        }, this, this);

        if (mMediaPlayer == null) {
            mPlaylistFragment.handleSongError();
        }

        return startTime;
    }

    private MediaPlayer mCurrentTrackingProgress;
    private Runnable mUpdateSongProgressRunnable;

    private void scheduleUpdateSongProgress() {
        mHandler.postDelayed(mUpdateSongProgressRunnable, 200);
    }

    public void playSong(SongData song) {
        playSong(song, -1);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        if (mMediaPlayer == mp) {
            mPlaylistFragment.handleSongError();
            resetMediaPlayer();
        }
        return true;
    }

    public void onPlaylistEnded() {
        toast("End of the playlist");
        resetMediaPlayer();
    }

    public void onPlayControlClicked() {
        boolean isPlaying = false;

        if (mMediaPlayer == null) {
            if (mPlaylist.isEmpty()) {
                toast("No song to play. Please add a song");
            } else {
                mPlaylistFragment.onSongSelected(0);
                return;
            }
        } else {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                sendMessageData(new SimpleMessage(MessageData.MESSAGE_TYPE_PAUSE_SONG));
            } else {
                mMediaPlayer.start();
                scheduleUpdateSongProgress();
                isPlaying = true;
                sendMessageData(new SimpleMessage(MessageData.MESSAGE_TYPE_RESUME_SONG));
            }
        }
        mPlaylistFragment.updatePlayControl(isPlaying);
    }

    public void onReceiveDeviceName(String deviceName) {
        if (mDeviceName != null) {
            return;
        }

        String name = getNameFromWifiName(deviceName);
        if (name == null) {
            name = deviceName;
        } else {
            updateWifiName(name, null);
        }
        mDeviceName = name;
    }

    public long getHostCurrentTime() {
        return System.currentTimeMillis() + mTimeDifferenceWithHost;
    }

    public boolean isHost() {
        return mIsHost;
    }

    private String getWifiName(String name) {
        return getString(R.string.wifi_name_format, SecurityHelper.encode(name), name);
    }

    // return null if deviceName does not fit the pattern
    private String getNameFromWifiName(String deviceName) {
        if (deviceName.startsWith(mWifiNamePrefix)) {
            int dividerIndex = deviceName.indexOf(mWifiNameDivider, mWifiNamePrefix.length());
            if (dividerIndex != -1) {
                String name;
                if (dividerIndex + 1 < deviceName.length()) {
                    name = deviceName.substring(dividerIndex + 1);
                } else {
                    name = "";
                }

                if (getWifiName(name).equals(deviceName)) {
                    return name;
                }
            }
        }
        return null;
    }

    // this method is used to forget all wifi-direct connections
    // in order to show request dialog again
    // http://stackoverflow.com/questions/15152817/can-i-change-the-group-owner-in-a-persistent-group-in-wi-fi-direct/26242221#26242221
    private void deletePersistentGroups() {
        try {
            Method[] methods = WifiP2pManager.class.getMethods();
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].getName().equals("deletePersistentGroup")) {
                    // Delete any persistent group
                    // Maximum remembered group is 32
                    for (int netId = 0; netId < 32; netId++) {
                        methods[i].invoke(mManager, mChannel, netId, null);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float x = sensorEvent.values[0];
        float y = sensorEvent.values[1];
        float z = sensorEvent.values[2];

        long sensorTime = System.currentTimeMillis();
        if (mLastSensorTime == 0) {
            // init time for the 1st time
            mLastSensorTime = sensorTime;
            mMovingCheckAnchorTime = sensorTime;
        }
        if (sensorTime - mLastSensorTime < SENSOR_TIME_INTERVAL) {
            return;
        }

        if (sensorTime - mMovingCheckAnchorTime > DANCING_TIME_INTERVAL) {
            if (mMovingCheck < DANCING_CHECK_NUMBER) {
                mIsDancing = false;
                updateDancingIndicator();
            }
            // reset
            mMovingCheck = 0;
            mMovingCheckAnchorTime = sensorTime;
        } else {
            long diffTime = (sensorTime - mLastSensorTime);
            mLastSensorTime = sensorTime;
            float speed = Math.abs(x + y + z - mLastX - mLastY - mLastZ) / diffTime * SENSOR_TIME_INTERVAL;

            if (speed > SHAKE_THRESHOLD) {
                mMovingCheck++;

                if (mMovingCheck == DANCING_CHECK_NUMBER) {
                    mIsDancing = true;
                    updateDancingIndicator();
                }
            }
        }
        mLastX = x;
        mLastY = y;
        mLastZ = z;
        mLastSensorTime = sensorTime;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void updateDancingIndicator() {
        if (mIndicatorItem != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mIndicatorItem.setIcon(mIsDancing ? R.drawable.ic_dancing : R.drawable.ic_standing);
                }
            });
        }
    }

    public void sendLikeData(SongData song, boolean isLike) {
        if (isLike && !mIsDancing) {
            mMaxHelper.showErrorDialog(this, "You need to dance in order to vote for a song");
            return;
        }

        song.mIsLiked = isLike;
        mPlaylistFragment.notifyAdapterChanges();
        sendDataToHost(new LikeData(mDeviceName, isLike, song.mId));
    }

    public void rearrangePlaylist(int position, boolean isUp) {
        int selected = mPlaylistFragment.getSelectedIndex();
        int swapPosition = isUp ? position - 1 : position + 1;
        if (swapPosition < 0 || swapPosition >= mPlaylist.size()) {
            return;
        }
        // swapping 2 song ids
        long tempId = mPlaylist.get(position);
        mPlaylist.set(position, mPlaylist.get(swapPosition));
        mPlaylist.set(swapPosition, tempId);
        // update current playing song ui
        if (selected == swapPosition) {
            selected = position;
        } else if (selected == position) {
            selected = swapPosition;
        }
        mPlaylistFragment.updateRearrangedPlaylist(selected);
        if (mIsHost) {
            sendMessageDataToAll(new SimpleMessage(MessageData.MESSAGE_TYPE_REARRANGE_PLAYLIST,
                    position, isUp));
        }
    }

    private final Object mPrivateChatFragmentsLock = new Object();
    private ArrayList<PrivateChatFragment> mPrivateChatFragments;

    private int findChatTabByName(String name) {
        synchronized (mPrivateChatFragmentsLock) {
            for (int i = 0; i < mPrivateChatFragments.size(); i++) {
                PrivateChatFragment fragment = mPrivateChatFragments.get(i);
                if (fragment.getName().equals(name)) {
                    return i;
                }
            }
            return -1;
        }
    }

    public void checkAndAddTab(String userName) {
        int tabIndex = findChatTabByName(userName);
        if (tabIndex == -1) {
            // add tab if not existed
            addTab(userName, true);
        } else {
            // ignore 3 default tabs
            mViewPager.setCurrentItem(tabIndex + 3);
        }
    }

    private PrivateChatFragment addTab(String userName, final boolean isSwitchToTab) {
        final PrivateChatFragment fragment = new PrivateChatFragment();
        fragment.setData(userName);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mPrivateChatFragmentsLock) {
                    mPrivateChatFragments.add(fragment);
                    mPartyPagerAdapter.addFragment(fragment);
                }
                if (isSwitchToTab) {
                    // the last tab is this newly added one
                    mViewPager.setCurrentItem(mPartyPagerAdapter.getCount() - 1);
                }
            }
        });
        return fragment;
    }

    public void sendPrivateChat(final PrivateChatFragment fragment, String message) {
        String target = fragment.getName();
        boolean isExist = false;
        synchronized (mUserNameLock) {
            for (String userName : mUserNames) {
                if (userName.equals(target)) {
                    isExist = true;
                    break;
                }
            }
        }

        if (!isExist) {
            mMaxHelper.showYesNoDialog(this, "User is no longer in room", "Remove conversation",
                    "Leave it be", new MaxHelper.YesNoDialog() {
                        @Override
                        public void onSelected(boolean isYes) {
                            if (isYes) {
                                synchronized (mPrivateChatFragmentsLock) {
                                    int fragmentIndex = mPrivateChatFragments.indexOf(fragment);
                                    mPrivateChatFragments.remove(fragmentIndex);
                                    mPartyPagerAdapter.remove(fragmentIndex);
                                    mViewPager.setCurrentItem(0);
                                }
                            }
                        }
                    });
            return;
        }
        PrivateChatData chatData = new PrivateChatData(message, 1, mDeviceName, target);
        sendMessageData(chatData);
        fragment.addChat(chatData.mOwner, chatData.mChat);
    }

    public void kickUser(String name) {
        final ChatSocketThread socket = findChatSocketThreadByName(name);
        if (socket != null) {
            socket.write(new SimpleMessage(MessageData.MESSAGE_TYPE_KICK).toString());
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    socket.closeSocket();
                }
            }, 2000);
            synchronized (mUserNameLock) {
                mUserNames.remove(name);
                mInfoFragment.removeUser(name);
            }
        }
    }
}
