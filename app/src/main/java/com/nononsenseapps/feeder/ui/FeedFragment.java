package com.nononsenseapps.feeder.ui;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.nononsenseapps.feeder.R;
import com.nononsenseapps.feeder.db.FeedItemSQL;
import com.nononsenseapps.feeder.db.FeedSQL;
import com.nononsenseapps.feeder.db.RssContentProvider;
import com.nononsenseapps.feeder.db.Util;
import com.squareup.picasso.Picasso;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.Locale;


public class FeedFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor> {

    // TODO change format possibly
    static final DateTimeFormatter shortDateTimeFormat =
            DateTimeFormat.shortDateTime().withLocale(Locale.getDefault());

    private static final int FEED_LOADER = 1;

    private static final String ARG_FEED_ID = "feed_id";
    private static final String ARG_FEED_TITLE = "feed_title";
    private static final String ARG_FEED_URL = "feed_url";
    private FeedAdapter mAdapter;
    private AbsListView mRecyclerView;
    //private LinearLayoutManager mLayoutManager;
    private boolean onlyUnread = true;
    // Filter for database loader
    private static final String AND_UNREAD = " AND " + FeedItemSQL.COL_UNREAD
                                             + " IS 1 ";
    // TODO change this
    private long id = -1;
    private String title = "Android Police Dummy";
    private String url = "http://feeds.feedburner.com/AndroidPolice";

    public FeedFragment() {
    }

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static FeedFragment newInstance(long id, String title, String url) {
        FeedFragment fragment = new FeedFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_FEED_ID, id);
        args.putString(ARG_FEED_TITLE, title);
        args.putString(ARG_FEED_URL, url);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ((FeedActivity) activity)
                .onFragmentAttached(getArguments().getString(ARG_FEED_TITLE));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            id = getArguments().getLong(ARG_FEED_ID, -1);
            title = getArguments().getString(ARG_FEED_TITLE);
            url = getArguments().getString(ARG_FEED_URL);
        }

        setHasOptionsMenu(true);

        // Load some RSS
        getLoaderManager().restartLoader(FEED_LOADER, new Bundle(), this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView =
                inflater.inflate(R.layout.fragment_feed, container, false);
        mRecyclerView = (AbsListView) rootView.findViewById(android.R.id.list);

        // improve performance if you know that changes in content
        // do not change the size of the RecyclerView
        //mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        //mLayoutManager = new LinearLayoutManager(getActivity());
        //mRecyclerView.setLayoutManager(mLayoutManager);

        // I want some dividers
        //mRecyclerView.addItemDecoration(new DividerItemDecoration
        //       (getActivity(), DividerItemDecoration.VERTICAL_LIST));

        // specify an adapter
        mAdapter = new FeedAdapter(getActivity());
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView
                .setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(final AdapterView<?> parent,
                            final View view, final int position,
                            final long id) {
                        // Just open in browser for now
                        ((FeedAdapter.ViewHolder) view.getTag()).onClick(view);
                    }
                });

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle bundle) {
        super.onActivityCreated(bundle);

        ((BaseActivity) getActivity()).enableActionBarAutoHide(mRecyclerView);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.feed_fragment, menu);

        if (id < 1) {
            menu.findItem(R.id.action_edit_feed).setVisible(false);
            menu.findItem(R.id.action_delete_feed).setVisible(false);
            menu.findItem(R.id.action_mark_as_read).setVisible(false);
            menu.findItem(R.id.action_only_unread).setVisible(false);
        }

        // Set toggleable state
        MenuItem menuItem = menu.findItem(R.id.action_only_unread);
        menuItem.setChecked(onlyUnread);
        // TODO use string resources
        menuItem.setTitle(onlyUnread ? "Show all" : "Only unread");

        // Don't forget super call here
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        final long id = menuItem.getItemId();
        if (id == R.id.action_edit_feed && this.id > 0) {
            Intent i = new Intent(getActivity(), EditFeedActivity.class);
            // TODO do not animate the back movement here
            i.putExtra(EditFeedActivity.SHOULD_FINISH_BACK, true);
            i.putExtra(EditFeedActivity._ID, this.id);
            i.putExtra(EditFeedActivity.TITLE, title);
            i.setData(Uri.parse(url));
            startActivity(i);
            return true;
        } else if (id == R.id.action_delete_feed && this.id > 0) {
            getActivity().getContentResolver()
                    .delete(FeedSQL.URI_FEEDS, Util.WHEREIDIS,
                            Util.LongsToStringArray(this.id));
            // TODO close fragment
            return true;
        } else if (id == R.id.action_mark_as_read && this.id > 0) {
            RssContentProvider.MarkFeedAsRead(getActivity(), this.id);
            return true;
        } else if (id == R.id.action_only_unread && this.id > 0) {
            onlyUnread = !menuItem.isChecked();
            menuItem.setChecked(onlyUnread);
            // TODO use string resources
            menuItem.setTitle(onlyUnread ? "Show all" : "Only unread");
            //getActivity().invalidateOptionsMenu();
            // Restart loader
            getLoaderManager().restartLoader(FEED_LOADER, new Bundle(), this);
            return true;
        } else {
            return super.onOptionsItemSelected(menuItem);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(final int ID, final Bundle bundle) {
        if (ID == FEED_LOADER) {
            return new CursorLoader(getActivity(), FeedItemSQL.URI_FEED_ITEMS,
                    FeedItemSQL.FIELDS, FeedItemSQL.COL_FEED +
                                        " IS ? " +
                                        (onlyUnread ? AND_UNREAD : ""),
                    Util.LongsToStringArray(this.id),
                    FeedItemSQL.COL_PUBDATE + " DESC");
        }
        return null;
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> cursorLoader,
            final Cursor cursor) {
        mAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> cursorLoader) {
        mAdapter.swapCursor(null);
    }


    class FeedAdapter extends SimpleCursorAdapter {

        // 64dp at xhdpi is 128 pixels
        private final int defImgWidth = 2 * 128;
        private final int defImgHeight = 2 * 128;

        private final int unreadTextColor;
        private final int readTextColor;

        public FeedAdapter(final Context context) {
            //super(context, R.layout.view_story);
            super(context, R.layout.view_story, null, new String[]{},
                    new int[]{}, 0);

            unreadTextColor = context.getResources()
                    .getColor(R.color.primary_text_default_material_dark);
            readTextColor = context.getResources()
                    .getColor(R.color.secondary_text_material_dark);
        }

        @Override
        public int getCount() {
            if (super.getCount() == 0) {
                return 0;
            } else {
                // 1 header + the rest
                return 1 + super.getCount();
            }
        }

        @Override
        public View getView(int hposition, View convertView, ViewGroup parent) {
            if (convertView == null) {
                if (getItemViewType(hposition) == 0) {
                    convertView = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.padding_header_item, parent,
                                    false);
                } else {
                    convertView = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.view_story, parent, false);
                    convertView.setTag(new ViewHolder(convertView));
                }
            }

            if (getItemViewType(hposition) == 0) {
                // Header
                return convertView;
            }

            // Item
            ViewHolder holder = (ViewHolder) convertView.getTag();
            // position in data set
            final int position = hposition - 1;
            final FeedItemSQL item =
                    new FeedItemSQL((Cursor) super.getItem(position));
            //final RssItem item = items.get(position);

            holder.rssItem = item;
            holder.link = item.link;
            if (item.getPubDate() == null) {
                holder.dateTextView.setVisibility(View.GONE);
            } else {
                holder.dateTextView.setVisibility(View.VISIBLE);
                holder.dateTextView.setText(
                        item.getPubDate().toString(shortDateTimeFormat));
            }
            if (item.plaintitle == null) {
                holder.titleTextView.setVisibility(View.GONE);
            } else {
                holder.titleTextView.setVisibility(View.VISIBLE);
                holder.titleTextView.setText(item.plaintitle);
                // Change depending on read status
                holder.titleTextView.setTextColor(holder.rssItem.isUnread() ?
                                                  unreadTextColor :
                                                  readTextColor);
            }
            if (item.plainsnippet == null) {
                holder.bodyTextView.setVisibility(View.GONE);
            } else {
                holder.bodyTextView.setVisibility(View.VISIBLE);
                //                holder.bodyTextView.setText(android.text.Html.fromHtml(item
                //                        .getDescription()));
                holder.bodyTextView.setText(item.plainsnippet);
            }
            if (item.imageurl == null) {
                holder.imageView.setVisibility(View.GONE);
            } else {
                int w = holder.imageView.getWidth();
                if (w <= 0) {
                    w = defImgWidth;
                }
                int h = holder.parent.getHeight();
                if (h <= 0) {
                    h = defImgHeight;
                }
                Picasso.with(getActivity()).load(item.imageurl).resize(w, h)
                        .centerCrop().into(holder.imageView);
                holder.imageView.setVisibility(View.VISIBLE);
            }

            return convertView;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 0;
            } else {
                return 1;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        // Provide a reference to the type of views that you are using
        public class ViewHolder {
            public final View parent;
            public final TextView titleTextView;
            public final TextView bodyTextView;
            public final TextView dateTextView;
            public final ImageView imageView;
            public String link;
            public FeedItemSQL rssItem;

            public ViewHolder(View v) {
                //v.setOnClickListener(this);
                parent = v;
                titleTextView = (TextView) v.findViewById(R.id.story_title);
                bodyTextView = (TextView) v.findViewById(R.id.story_body);
                dateTextView = (TextView) v.findViewById(R.id.story_date);
                imageView = (ImageView) v.findViewById(R.id.story_image);
            }

            /**
             * OnItemClickListener replacement.
             * <p/>
             * If a feeditem does not have any content,
             * then it opens the link in the browser directly.
             *
             * @param view
             */
            //            @TargetApi(Build.VERSION_CODES.L)
            //            @Override
            public void onClick(final View view) {

                // Open item if not empty
                if (rssItem.description != null &&
                    !rssItem.description.isEmpty()) {
                    Intent i = new Intent(getActivity(), ReaderActivity.class);
                    //i.setData(Uri.parse(link));
                    i.putExtra(BaseActivity.SHOULD_FINISH_BACK, true);
                    ReaderActivity.setRssExtras(i, rssItem.id, rssItem);

                    // TODO add animation
                    Log.d("JONAS", "View size: w: " + view.getWidth() +
                                   ", h: " + view.getHeight());
                    Log.d("JONAS", "View pos: l: " + view.getLeft() +
                                   ", t: " + view.getTop());
                    ActivityOptions options = ActivityOptions
                            .makeScaleUpAnimation(view, 0, 0, view.getWidth(),
                                    view.getHeight());

                    startActivity(i, options.toBundle());
                } else {
                    // Open in browser since no content was posted
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(rssItem.link)));
                }
            }

                /*
                Intent story = new Intent(getActivity(), StoryActivity.class);
                story.putExtra("title", titleTextView.getText());
                story.putExtra("body", bodyTextView.getText());

                Bundle activityOptions = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.L) {
                    ActivityOptions options = ActivityOptions
                            .makeSceneTransitionAnimation(getActivity(),
                                    new Pair<View, String>(titleTextView,
                                            "title"),
                                    new Pair<View, String>(bodyTextView,
                                            "body"),
                                    new Pair<View, String>(imageView, "image"));

                    getActivity().setExitSharedElementListener(new SharedElementListener() {
                                @Override
                                public void remapSharedElements(List<String> names,
                                        Map<String, View> sharedElements) {
                                    super.remapSharedElements(names,
                                            sharedElements);
                                    sharedElements.put("title", titleTextView);
                                    sharedElements.put("body", bodyTextView);
                                    sharedElements.put("image", imageView);
                                }
                            });
                    activityOptions = options.toBundle();
                }

                startActivity(story, activityOptions);*/
            //            }
        }
    }
}
