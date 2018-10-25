package com.example.xyzreader.ui;

import android.app.ActivityOptions;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

public class ArticleListActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor>, SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = ArticleListActivity.class.getSimpleName();
    private static final String FRAGMENT_TAG = "DetailsFragment";

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;
    private boolean mTwoPane = false;

    private long mCurrentID = -1;
    private static final String CURRENT_ID = "current_id";

    private boolean mIsRefreshing = false;

    public static final int LOADER_ID = 1;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2, 1, 1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);
        }
        setContentView(R.layout.activity_article_list);
        setToolbar();

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        getLoaderManager().initLoader(LOADER_ID, null, this);

        if (findViewById(R.id.item_details_container) != null) {
            mTwoPane = true;
        } else {
            mTwoPane = false;
        }

        if (savedInstanceState == null) {
            refresh();
        } else {
            if (savedInstanceState.containsKey(CURRENT_ID)) {
                mCurrentID = savedInstanceState.getLong(CURRENT_ID);
            }

            if (mTwoPane) {
                loadDetailsFragment();
            }
        }
    }

    private void setToolbar() {
        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(null);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLong(CURRENT_ID, mCurrentID);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    @Override
    public void onRefresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        Adapter adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        GridLayoutManager layoutManager =
                new GridLayoutManager(this, columnCount, GridLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(adapter);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private void startDetailsActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW, ItemsContract.Items.buildItemUri(mCurrentID));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Bundle bundle = ActivityOptions
                    .makeSceneTransitionAnimation(this)
                    .toBundle();

            startActivity(intent, bundle);
        } else {
            startActivity(intent);
        }
    }

    private void loadDetailsFragment() {
        ArticleDetailFragment fragment = ArticleDetailFragment.newInstance(mCurrentID);

        getFragmentManager().beginTransaction()
                .replace(R.id.item_details_container, fragment, FRAGMENT_TAG)
                .commit();
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mCurrentID = (int) getItemId(vh.getAdapterPosition());
                    if (mTwoPane) {
                        loadDetailsFragment();
                    } else {
                        startDetailsActivity();
                    }
                }
            });
            return vh;
        }

        private Date parsePublishedDate() {
            try {
                String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
                return dateFormat.parse(date);
            } catch (ParseException ex) {
                Log.e(TAG, ex.getMessage());
                Log.i(TAG, "passing today's date");
                return new Date();
            }
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            mCursor.moveToPosition(position);

            Glide.with(ArticleListActivity.this)
                    .asBitmap()
                    .load(mCursor.getString(ArticleLoader.Query.THUMB_URL))
                    .apply(new RequestOptions().placeholder(R.color.colorPrimary).error(R.color.colorPrimary))
                    .into(new BitmapImageViewTarget(holder.thumbnailView) {
                        @Override
                        public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                            super.onResourceReady(bitmap, transition);

                            Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
                                @Override
                                public void onGenerated(@NonNull Palette palette) {
                                    holder.titleBackground.setBackgroundColor
                                            (palette.getVibrantColor(ContextCompat.getColor(ArticleListActivity.this, R.color.black_translucent_60)));
                                }
                            });
                        }
                    });

            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));

            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {
                holder.subtitleView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + "<br/>"
                                + " by <font color='#ffffff'>"
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));
            } else {
                holder.subtitleView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate)
                                + "<br/>"
                                + " by <font color='#ffffff'>"
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));
            }
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView thumbnailView;
        public View titleBackground;
        public TextView titleView;
        public TextView subtitleView;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (ImageView) view.findViewById(R.id.thumbnail);
            titleBackground = (View) view.findViewById(R.id.title_background);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
        }
    }

//    private void setArticles(Cursor cursor) {
//        ArrayList<Article> articles = new ArrayList<>();
//
//        if (cursor != null && cursor.moveToFirst()) {
//            do {
//                Article article = new Article();
//
//                article.setId(cursor.getInt(cursor.getColumnIndex(ItemsContract.Items._ID)));
////                article.setServerId(cursor.getString(cursor.getColumnIndex(ItemsContract.Items.SERVER_ID)));
//                article.setTitle(cursor.getString(cursor.getColumnIndex(ItemsContract.Items.TITLE)));
//                article.setPublishDate(cursor.getString(cursor.getColumnIndex(ItemsContract.Items.PUBLISHED_DATE)));
//                article.setAuthor(cursor.getString(cursor.getColumnIndex(ItemsContract.Items.AUTHOR)));
//                article.setThumbUrl(cursor.getString(cursor.getColumnIndex(ItemsContract.Items.THUMB_URL)));
//                article.setPhotoUrl(cursor.getString(cursor.getColumnIndex(ItemsContract.Items.PHOTO_URL)));
////                article.setAspectRatio(cursor.getFloat(cursor.getColumnIndex(ItemsContract.Items.ASPECT_RATIO)));
//                article.setBody(cursor.getString(cursor.getColumnIndex(ItemsContract.Items.BODY)));
//
//                articles.add(article);
//            } while (cursor.moveToNext());
//        }
//
//        try {
//            if (cursor != null && !cursor.isClosed()) {
//                cursor.close();
//            }
//        } catch (Exception e) {
//            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
//        }
//
//        this.articles.clear();
//        this.articles.addAll(articles);
//    }
}
