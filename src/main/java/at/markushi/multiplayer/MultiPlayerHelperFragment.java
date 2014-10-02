package at.markushi.multiplayer;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class MultiPlayerHelperFragment extends Fragment {

	public interface MultiplayerHelperFragmentHost {

		void setMultiPlayerHelper(MultiPlayerHelper helper);
	}

	private MultiPlayerHelper helper;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return new View(getActivity());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (helper != null) {
			helper.onDestroy();
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		helper.setMultiPlayerUi(null);
		helper.setActivity(null);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		MultiplayerHelperFragmentHost homeActivity = (MultiplayerHelperFragmentHost) activity;

		if (helper == null) {
			helper = new MultiPlayerHelper(activity);
			homeActivity.setMultiPlayerHelper(helper);
			helper.onCreate();
		} else {
			helper.setActivity(activity);
			homeActivity.setMultiPlayerHelper(helper);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		helper.onActivityResult(requestCode, resultCode, data);
	}

	public MultiPlayerHelper getHelper() {
		return helper;
	}

}