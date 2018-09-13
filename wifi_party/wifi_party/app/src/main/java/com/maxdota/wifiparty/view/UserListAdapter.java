package com.maxdota.wifiparty.view;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.maxdota.wifiparty.R;

import java.util.ArrayList;

/**
 * Created by Varsha on 12/19/2016.
 */
public class UserListAdapter extends RecyclerView.Adapter<UserListAdapter.SimpleItemViewHolder> {
    private Context mContext;
    private LayoutInflater mLayoutInflater;

    private ArrayList<String> mDataList;
    private UserListListener mListener;
    private boolean mIsHost;

    public UserListAdapter(ArrayList<String> dataList, boolean isHost, UserListListener listener) {
        mDataList = dataList;
        mListener = listener;
        mIsHost = isHost;
    }

    public void updateIsHost(boolean isHost) {
        mIsHost = isHost;
        notifyDataSetChanged();
    }

    @Override
    public SimpleItemViewHolder onCreateViewHolder(ViewGroup parent, int position) {
        if (mContext == null) {
            mContext = parent.getContext();
            mLayoutInflater = LayoutInflater.from(mContext);
        }
        View view = mLayoutInflater.inflate(R.layout.list_item_user, parent, false);
        return new SimpleItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(SimpleItemViewHolder viewHolder, int position) {
        viewHolder.bindAttendance(position + 1);
    }

    @Override
    public int getItemCount() {
        return mDataList.size() - 1;
    }

    class SimpleItemViewHolder extends RecyclerView.ViewHolder {
        private View mListContainer;
        private TextView mSimpleText;
        private View mDeleteIcon;

        private int mListPosition;

        SimpleItemViewHolder(View view) {
            super(view);
            mListPosition = -1;

            mListContainer = view.findViewById(R.id.list_container);
            mDeleteIcon = view.findViewById(R.id.delete_icon);
            mSimpleText = (TextView) view.findViewById(R.id.simple_text);

            mListContainer.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (mListener != null) {
                        mListener.onItemClicked(mListPosition);
                    }
                    return true;
                }
            });

            mDeleteIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mListener != null) {
                        mListener.onDeleteClicked(mListPosition);
                    }
                }
            });
        }

        void bindAttendance(int position) {
            mListPosition = position;
            mSimpleText.setText(mDataList.get(position));
            if (mIsHost) {
                mDeleteIcon.setVisibility(View.VISIBLE);
            } else {
                mDeleteIcon.setVisibility(View.GONE);
            }
        }
    }

    public interface UserListListener {
        void onItemClicked(int position);

        void onDeleteClicked(int position);
    }
}