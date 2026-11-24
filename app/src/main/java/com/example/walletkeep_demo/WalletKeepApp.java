package com.example.walletkeep_demo;

import android.app.Application;

import com.example.walletkeep_demo.storage.PrefsManager;

/**
 * 应用程序入口，负责在启动时应用已保存的浅色/深色主题设置。
 */
public class WalletKeepApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        new PrefsManager(this).applySavedTheme();
    }
}
