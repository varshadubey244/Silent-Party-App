package com.maxdota.wifiparty.fragment;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import com.maxdota.maxhelper.base.BaseFragment;

import java.util.ArrayList;

/**
 * Created by Varsha on 12/2/2016.
 */

public class PartyPagerAdapter extends FragmentPagerAdapter {
    private ArrayList<BaseFragment> mFragments;

    public PartyPagerAdapter(FragmentManager fm, ArrayList<BaseFragment> fragments) {
        super(fm);
        mFragments = fragments;
    }

    public void addFragment(BaseFragment fragment) {
        mFragments.add(fragment);
        notifyDataSetChanged();
    }

    @Override
    public Fragment getItem(int position) {
        return mFragments.get(position);
    }

    @Override
    public int getCount() {
        return mFragments.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return mFragments.get(position).getName();
    }

    public void remove(int fragmentIndex) {
        mFragments.remove(fragmentIndex);
        notifyDataSetChanged();
    }
}
