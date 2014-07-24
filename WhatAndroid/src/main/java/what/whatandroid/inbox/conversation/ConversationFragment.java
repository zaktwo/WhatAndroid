package what.whatandroid.inbox.conversation;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import api.inbox.conversation.Conversation;
import api.soup.MySoup;
import what.whatandroid.R;
import what.whatandroid.callbacks.AddQuoteCallback;
import what.whatandroid.callbacks.OnLoggedInCallback;
import what.whatandroid.comments.CommentsAdapter;
import what.whatandroid.forums.thread.ReplyDialogFragment;

/**
 * Fragment for displaying the list of messages in a conversation
 */
public class ConversationFragment extends Fragment implements OnLoggedInCallback,
	AddQuoteCallback, LoaderManager.LoaderCallbacks<Conversation> {

	public static final String CONVERSATION = "what.whatandroid.conversationfragment.CONVERSATION";

	private ProgressBar loadingIndicator;
	/**
	 * The conversation being viewed
	 */
	private Conversation conversation;
	/**
	 * Adapter displaying the messages in the conversation
	 */
	private CommentsAdapter adapter;
	private ListView list;
	/**
	 * Draft of the reply we're writing for this conversation
	 */
	private String replyDraft = "";

	/**
	 * Create a conversation fragment displaying the messages in the
	 * desired conversation
	 *
	 * @param id id of conversation to view
	 */
	public static ConversationFragment newInstance(int id){
		ConversationFragment f = new ConversationFragment();
		Bundle args = new Bundle();
		args.putInt(CONVERSATION, id);
		f.setArguments(args);
		return f;
	}

	public ConversationFragment(){
		//Required empty ctor
	}

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		if (savedInstanceState != null){
			replyDraft = savedInstanceState.getString(ReplyDialogFragment.DRAFT);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
		View view = inflater.inflate(R.layout.fragment_list_view, container, false);
		list = (ListView)view.findViewById(R.id.list);
		loadingIndicator = (ProgressBar)view.findViewById(R.id.loading_indicator);
		adapter = new CommentsAdapter(getActivity());
		list.setAdapter(adapter);
		if (MySoup.isLoggedIn()){
			getLoaderManager().initLoader(0, getArguments(), this);
		}
		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState){
		super.onSaveInstanceState(outState);
		outState.putString(ReplyDialogFragment.DRAFT, replyDraft);
	}

	@Override
	public void onLoggedIn(){
		if (isAdded()){
			getLoaderManager().initLoader(0, getArguments(), this);
		}
	}

	@Override
	public void quote(String quote){
		replyDraft += quote;
		showReplyDialog();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data){
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 0){
			switch (resultCode){
				case ReplyDialogFragment.DISCARD:
					replyDraft = "";
					break;
				case ReplyDialogFragment.SAVE_DRAFT:
					replyDraft = data.getStringExtra(ReplyDialogFragment.DRAFT);
					break;
				case ReplyDialogFragment.POST_REPLY:
					replyDraft = "";
					String reply = data.getStringExtra(ReplyDialogFragment.DRAFT);
					new PostReplyTask().execute(reply);
					break;
				default:
					break;
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
		inflater.inflate(R.menu.conversation, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		switch (item.getItemId()){
			//Show the reply dialog so the user can write their post
			case R.id.action_reply:
				showReplyDialog();
				break;
			default:
				break;
		}
		return false;
	}

	/**
	 * Display the compose reply dialog so the user can write their response
	 */
	private void showReplyDialog(){
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		Fragment prev = getFragmentManager().findFragmentByTag("dialog");
		if (prev != null){
			ft.remove(prev);
		}
		ft.addToBackStack(null);
		ReplyDialogFragment reply = ReplyDialogFragment.newInstance(replyDraft);
		reply.setTargetFragment(this, 0);
		reply.show(ft, "dialog");
	}

	@Override
	public Loader<Conversation> onCreateLoader(int id, Bundle args){
		loadingIndicator.setVisibility(View.VISIBLE);
		return new ConversationAsyncLoader(getActivity(), getArguments());
	}

	@Override
	public void onLoadFinished(Loader<Conversation> loader, Conversation data){
		loadingIndicator.setVisibility(View.GONE);
		if (data == null || data.getResponse() == null || !data.getStatus()){
			Toast.makeText(getActivity(), "Could not load conversation", Toast.LENGTH_LONG).show();
		}
		else {
			conversation = data;
			if (adapter.isEmpty()){
				adapter.addAll(data.getResponse().getMessages());
				adapter.notifyDataSetChanged();
				//Jump to the most recent post in the conversation
				list.setSelection(list.getCount() - 1);
			}
		}
	}

	@Override
	public void onLoaderReset(Loader<Conversation> loader){
		conversation = null;
		adapter.clear();
		adapter.notifyDataSetChanged();
	}

	private class PostReplyTask extends AsyncTask<String, Void, Boolean> {
		@Override
		protected Boolean doInBackground(String... params){
			return conversation.reply(params[0]);
		}

		@Override
		protected void onPostExecute(Boolean status){
			if (!status){
				Toast.makeText(getActivity(), "Could not post reply", Toast.LENGTH_LONG).show();
			}
			else {
				//Reload the new posts to show
				getLoaderManager().destroyLoader(0);
				getLoaderManager().initLoader(0, getArguments(), ConversationFragment.this);
			}
		}
	}
}