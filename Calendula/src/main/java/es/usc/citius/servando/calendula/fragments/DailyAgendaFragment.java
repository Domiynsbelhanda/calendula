package es.usc.citius.servando.calendula.fragments;

import android.animation.FloatEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.tjerkw.slideexpandable.library.SlideExpandableListAdapter;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.HomeActivity;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.AgendaZoomHelper;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.util.AvatarMgr;
import es.usc.citius.servando.calendula.util.DailyAgendaItemStub;

/**
 * Created by joseangel.pineiro on 11/15/13.
 */
public class DailyAgendaFragment extends Fragment implements HomeActivity.OnBackPressedListener {

    private static final String TAG = DailyAgendaFragment.class.getName();
    DailyAgendaItemStubComparator dailyAgendaItemStubComparator =
            new DailyAgendaItemStubComparator();
    List<DailyAgendaItemStub> items = new ArrayList<>();
    HomeUserInfoFragment userProInfoFragment;

    ArrayAdapter adapter = null;
    ListView listview = null;
    int lastScroll = 0;
    View userInfoFragment;
    boolean showPlaceholder = false;
    boolean expanded = false;
    int profileFragmentHeight = 0;
    View zoomContainer;
    AgendaZoomHelper zoomHelper;
    private SlideExpandableListAdapter slideAdapter;
    private int toolbarHeight;
    private int statusbarHeight;
    private int lastVisibleItemCount;
    private Dictionary<Integer, Integer> listViewItemHeights = new Hashtable<Integer, Integer>();

    @Override
    public void onDetach() {
        super.onDetach();
        try {
            Field childFragmentManager = Fragment.class.getDeclaredField("mChildFragmentManager");
            childFragmentManager.setAccessible(true);
            childFragmentManager.set(this, null);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        items = new ArrayList<>();
        items.addAll(buildItems()); // allow user to change day
    }

    public void showReminder(Routine r) {
        zoomHelper.remind(getActivity(), r);
    }

    public void showReminder(Schedule s, LocalTime t) {
        zoomHelper.remind(getActivity(), s, t);
    }

    public void showDelayDialog(Routine r) {
        zoomHelper.showDelayDialog(r);
    }

    public void showDelayDialog(Schedule s, LocalTime t) {
        zoomHelper.showDelayDialog(s, t);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_daily_agenda, container, false);
        listview = (ListView) rootView.findViewById(R.id.listview);

        inflater.inflate(R.layout.fragment_edit_profile, container, false);

        Log.d(getTag(), "Fragments: " + (getChildFragmentManager().getFragments() != null
                ? getChildFragmentManager().getFragments().size() : 0));
        userProInfoFragment = HomeUserInfoFragment.newInstance();
        profileFragmentHeight = (int) getResources().getDimension(R.dimen.header_height);
        toolbarHeight = (int) getResources().getDimension(R.dimen.action_bar_height);
        statusbarHeight = (int) getResources().getDimension(R.dimen.status_bar_height);

        getChildFragmentManager().beginTransaction()
                .replace(R.id.user_info_fragment, userProInfoFragment)
                .commit();

        userInfoFragment = rootView.findViewById(R.id.user_info_fragment);
        zoomContainer = rootView.findViewById(R.id.zoom_container);
        zoomHelper = new AgendaZoomHelper(zoomContainer, getActivity(),
                new AgendaZoomHelper.ZoomHelperListener() {
                    @Override
                    public void onChange() {
                        items.clear();
                        items.addAll(buildItems()); // allow user to change day
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onHide() {
                        //updateBackground(DateTime.now());
                    }

                    @Override
                    public void onShow(Routine r) {
                        // updateBackground(r.time().toDateTimeToday());
                    }
                });

        if (Build.VERSION.SDK_INT >= 11) {
            listview.setOnScrollListener(new AbsListView.OnScrollListener() {

                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {

                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

                    if (expanded && getUserVisibleHint()) { // expanded and visible
                        int scrollY = getScroll();
                        int scrollDiff = (scrollY - lastScroll);

                        if (Math.abs(lastVisibleItemCount - visibleItemCount) <= 5) {

                            int translationY = (int) (userInfoFragment.getTranslationY() - scrollDiff);

                            if (translationY < -profileFragmentHeight) {
                                translationY = -profileFragmentHeight;
                            } else if (translationY >= 0) {
                                translationY = 0;
                            }

                            if (translationY < toolbarHeight - profileFragmentHeight) {
                                ((HomeActivity) getActivity()).hideToolbar();
                            } else if (translationY > (toolbarHeight - profileFragmentHeight)) {
                                ((HomeActivity) getActivity()).showToolbar();
                            }
                            userInfoFragment.setTranslationY(translationY);
                        }
                        lastScroll = scrollY;
                    }
                    lastVisibleItemCount = visibleItemCount;
                }
            });
        }

        LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(getActivity(), R.anim.list_animation);
        listview.setLayoutAnimation(controller);

        new LoadDailyAgendaTask().execute(null, null, null);

        return rootView;
    }

    private int getScroll() {
        View c = listview.getChildAt(0); //this is the first visible row
        if (c != null) {
            int scrollY = -c.getTop();
            listViewItemHeights.put(listview.getFirstVisiblePosition(), c.getHeight());
            for (int i = 0; i < listview.getFirstVisiblePosition(); ++i) {
                if (listViewItemHeights.get(i) != null) // (this is a sanity check)
                {
                    scrollY +=
                            listViewItemHeights.get(i); //add all heights of the views that are gone
                }
            }
            return scrollY;
        }
        return 0;
    }

    public int getNextRoutinePosition() {
        int now = DateTime.now().getHourOfDay();
        for (Routine r : Routine.findAll()) {
            if (r.time().getHourOfDay() >= now) {
                return r.time().getHourOfDay();
            }
        }
        return 0;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            Log.d(getTag(), "Visible to user");
        }
    }

    public List<DailyAgendaItemStub> buildItems() {
        showPlaceholder = false;
        int now = DateTime.now().getHourOfDay();
        //String nextRoutineTime = getNextRoutineHour();
        ArrayList<DailyAgendaItemStub> items = new ArrayList<DailyAgendaItemStub>();
        addSpacerTop(items);

        ///////////////////////////////////////

        // get hourly schedules for today
        DateTime from = DateTime.now().withTimeAtStartOfDay();
        DateTime to = from.plusDays(1);

        final List<Schedule> hourly = DB.schedules().findHourly();
        Log.d(TAG, "Hourly schedules: " + hourly.size());
        for (Schedule s : hourly) {
            final List<DateTime> times = s.rule().occurrencesBetween(from, to, s.startDateTime());
            Log.d(TAG, "RFC - From : " + from.toString("E dd MMM, kk:mm ZZZ"));
            Log.d(TAG, "RFC - To : " + from.toString("E dd MMM, kk:mm ZZZ"));
            Log.d(TAG, "Hourly schedules times: " + times.size());
            if (times.size() > 0) {
                for (DateTime t : times) {
                    if (t.plusHours(1).isAfterNow() || expanded) {
                        Log.d(TAG, "RFC dailyAgenda: " + t.toString("E dd MMM, kk:mm ZZZ"));
                        DailyAgendaItemStub item = new DailyAgendaItemStub(t.toLocalTime());
                        item.meds = new ArrayList<>();
                        item.hasEvents = true;
                        int minute = t.getMinuteOfHour();
                        Medicine med = s.medicine();
                        DailyAgendaItemStub.DailyAgendaItemStubElement el =
                                new DailyAgendaItemStub.DailyAgendaItemStubElement();
                        el.medName = med.name();
                        el.dose = s.dose();
                        el.displayDose = s.displayDose();
                        el.res = med.presentation().getDrawable();
                        el.presentation = med.presentation();
                        el.minute = minute < 10 ? "0" + minute : String.valueOf(minute);
                        el.taken = DB.dailyScheduleItems()
                                .findByScheduleAndTime(s, t.toLocalTime())
                                .takenToday();
                        item.meds.add(el);
                        item.id = s.getId();
                        item.patient = s.patient();
                        item.isRoutine = false;
                        item.title = med.name();
                        item.hour = t.getHourOfDay();
                        item.minute = t.getMinuteOfHour();
                        item.time = new LocalTime(item.hour, item.minute);
                        items.add(item);
                    }
                }
            }
        }

        ////////////////////////////////////////

        for (int i = 0; i < 24; i++) {
            List<DailyAgendaItemStub> hourItems = DailyAgendaItemStub.fromHour(i);
            for (DailyAgendaItemStub item : hourItems) {
                if (item.hasEvents && i >= now || expanded) {
                    //if (item.time.toString("kk:mm") == nextRoutineTime)
                    //    item.isNext = true;
                    items.add(item);
                }
            }
        }

        Log.d(getTag(), "Items: " + items.size());
        if (items.size() == 1) {
            showPlaceholder = true;
            addEmptyPlaceholder(items);
        }

        Collections.sort(items, dailyAgendaItemStubComparator);

        for (DailyAgendaItemStub i : items) {
            if (i.hasEvents && i.time != null && i.time.toDateTimeToday().isAfterNow()) {
                Log.d(TAG, "isNext: " + i.time.toString("kk:mm"));
                i.isNext = true;
                break;
            }
        }

        return items;
    }

    private void addEmptyPlaceholder(ArrayList<DailyAgendaItemStub> items) {
        DailyAgendaItemStub spacer = new DailyAgendaItemStub(null);
        spacer.isEmptyPlaceholder = true;
        items.add(spacer);
    }

    private void addSpacerTop(ArrayList<DailyAgendaItemStub> items) {
        DailyAgendaItemStub spacer = new DailyAgendaItemStub(null);
        spacer.isSpacer = true;
        items.add(spacer);
    }

    public void toggleViewMode() {
        //update expand/collapse flag
        expanded = !expanded;
        // get next routine item index
        final int nextRoutineHour = getNextRoutinePosition();

        List<DailyAgendaItemStub> dailyAgendaItemStubs = buildItems();

        // restore header if not expanded
        if (!expanded) {
            restoreHeader();
        }

        // refresh adapter items
        items.clear();
        items.addAll(dailyAgendaItemStubs);

        // set expand/collapse animation
        listview.setLayoutAnimation(getAnimationController(expanded));
        // perform update
        adapter.notifyDataSetChanged();
        // open next routine item
        slideAdapter.setLastOpenPosition(expanded ? nextRoutineHour + 1 : 1);
    }

    LayoutAnimationController getAnimationController(boolean expand) {
        return AnimationUtils.loadLayoutAnimation(getActivity(),
                expand ? R.anim.list_animation_expand : R.anim.list_animation);
    }

    private View getAgendaItemView(int position, View convertView, ViewGroup parent) {

        final LayoutInflater layoutInflater = getActivity().getLayoutInflater();

        final DailyAgendaItemStub item = items.get(position);

        View v;

        if (item.isSpacer) {
            v = layoutInflater.inflate(R.layout.daily_view_spacer, null);
        } else if (item.isEmptyPlaceholder) {
            v = layoutInflater.inflate(R.layout.daily_view_empty_placeholder, null);
        } else if (!item.hasEvents && expanded) {
            v = layoutInflater.inflate(R.layout.daily_view_empty_hour, null);
            // select the correct layout
            ((TextView) v.findViewById(R.id.hour_text)).setText(item.time.toString("kk:mm"));
            v.findViewById(R.id.bottom).setVisibility(View.GONE);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int hour = Integer.valueOf((String) v.getTag());
                    onClickEmptyHour(hour);
                }
            });
        } else {



            v = layoutInflater.inflate(R.layout.daily_view_intake, null);
            LinearLayout medList = (LinearLayout) v.findViewById(R.id.med_item_list);

            for (DailyAgendaItemStub.DailyAgendaItemStubElement element : item.meds) {
                View medNameView = layoutInflater.inflate(R.layout.daily_view_intake_med, null);
                ((TextView) medNameView.findViewById(R.id.med_item_name)).setText(element.medName);

                if (element.taken) {
                    medNameView.findViewById(R.id.ic_done).setVisibility(View.VISIBLE);
                } else {
                    medNameView.findViewById(R.id.ic_done).setVisibility(View.INVISIBLE);
                }

                ((TextView) medNameView.findViewById(R.id.med_item_dose)).setText(
                        element.displayDose + " " +
                                (element.presentation.units(getResources())) + (element.dose > 1 ? "s"
                                : ""));
                ((ImageView) medNameView.findViewById(R.id.imageView)).setImageResource(
                        element.res);
                medList.addView(medNameView);
            }

            if (!item.isRoutine) {
                ((ImageButton) v.findViewById(R.id.imageButton2)).setImageResource(R.drawable.ic_history_black_48dp);
            }

            if(item.patient != null){
                ((ImageView) v.findViewById(R.id.patient_avatar)).setImageResource(AvatarMgr.res(item.patient.avatar()));
            }

            ((TextView) v.findViewById(R.id.routines_list_item_name)).setText(item.title);
            ((TextView) v.findViewById(R.id.routines_list_item_hour)).setText((item.hour > 9 ? item.hour : "0" + item.hour) + ":");
            ((TextView) v.findViewById(R.id.routines_list_item_minute)).setText((item.minute > 9 ? item.minute : "0" + item.minute) + "");

            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (item.isRoutine) {
                        zoomHelper.show(getActivity(), v, Routine.findById(item.id));
                    } else {
                        zoomHelper.show(getActivity(), v, Schedule.findById(item.id),
                                new LocalTime(item.hour, item.minute));
                    }
                }
            });
        }
        v.setTag("" + item.hour);
        return v;
    }

    private void onClickEmptyHour(int hour) {
        //Toast.makeText(getActivity(), " Add new routine or med here!", Toast.LENGTH_SHORT).show();
    }

    private void restoreHeader() {
        // translate header to its original position (y=0)
        ObjectAnimator.ofObject(userInfoFragment, "translationY", new FloatEvaluator(),
                (int) userInfoFragment.getTranslationY(), 0).setDuration(300).start();
    }

    void updatePrefs() {
        SharedPreferences settings =
                getActivity().getSharedPreferences(CalendulaApp.PREFERENCES_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();

        editor.commit();
    }

    @Override
    public void onPause() {
        super.onPause();
        updatePrefs();
    }

    @Override
    public boolean doBack() {
        if (zoomContainer.getVisibility() == View.VISIBLE) {
            zoomHelper.hide();
            return true;
        }
        return false;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void notifyDataChange() {

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                items.clear();
                items.addAll(buildItems()); // allow user to change day
                adapter.notifyDataSetChanged();
            }
        });

    }

    private class AgendaItemAdapter extends ArrayAdapter<DailyAgendaItemStub> {

        public AgendaItemAdapter(Context context, int layoutResourceId,
                                 List<DailyAgendaItemStub> items) {
            super(context, layoutResourceId, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getAgendaItemView(position, convertView, parent);
        }
    }

    public class LoadDailyAgendaTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            items = buildItems(); // allow user to change day
            return null;
        }

        @Override
        protected void onPostExecute(final Void result) {
            adapter = new AgendaItemAdapter(getActivity(), R.layout.daily_view_hour, items);
            slideAdapter =
                    new SlideExpandableListAdapter(adapter, R.id.count_container, R.id.bottom, 1);
            listview.setAdapter(slideAdapter);
        }
    }

    private class DailyAgendaItemStubComparator implements Comparator<DailyAgendaItemStub> {

        @Override
        public int compare(DailyAgendaItemStub a, DailyAgendaItemStub b) {

            if (a.isSpacer) {
                return -1;
            } else if (b.isSpacer) {
                return 1;
            }

            //String aTime = (a.hour>9?a.hour:"0"+a.hour)+":"+(a.minute>9?a.minute:"0"+a.minute);
            //String bTime = (b.hour>9?b.hour:"0"+b.hour)+":"+(b.minute>9?b.minute:"0"+b.minute);
            //return aTime.compareTo(bTime);
            return a.time.compareTo(b.time);
        }
    }
}