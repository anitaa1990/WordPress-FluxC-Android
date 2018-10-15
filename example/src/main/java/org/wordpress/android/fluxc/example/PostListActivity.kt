package org.wordpress.android.fluxc.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.util.DiffUtil
import android.support.v7.util.DiffUtil.DiffResult
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.ViewHolder
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.post_list_activity.*
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.isActive
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.PostActionBuilder
import org.wordpress.android.fluxc.model.PostModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.list.ListDescriptor
import org.wordpress.android.fluxc.model.list.ListItemDataSource
import org.wordpress.android.fluxc.model.list.ListManager
import org.wordpress.android.fluxc.model.list.ListOrder
import org.wordpress.android.fluxc.model.list.PostListDescriptor
import org.wordpress.android.fluxc.model.list.PostListDescriptor.PostListDescriptorForRestSite
import org.wordpress.android.fluxc.model.list.PostListDescriptor.PostListDescriptorForRestSite.PostStatusForRestSite
import org.wordpress.android.fluxc.model.list.PostListDescriptor.PostListDescriptorForXmlRpcSite
import org.wordpress.android.fluxc.model.list.PostListOrderBy
import org.wordpress.android.fluxc.persistence.PostSqlUtils
import org.wordpress.android.fluxc.store.ListStore
import org.wordpress.android.fluxc.store.ListStore.OnListChanged
import org.wordpress.android.fluxc.store.ListStore.OnListItemsChanged
import org.wordpress.android.fluxc.store.PostStore
import org.wordpress.android.fluxc.store.PostStore.FetchPostListPayload
import org.wordpress.android.fluxc.store.PostStore.RemotePostPayload
import org.wordpress.android.fluxc.store.SiteStore
import java.util.Random
import javax.inject.Inject

private const val LOCAL_SITE_ID = "LOCAL_SITE_ID"

class PostListActivity : AppCompatActivity() {
    @Inject internal lateinit var dispatcher: Dispatcher
    @Inject internal lateinit var listStore: ListStore
    @Inject internal lateinit var postStore: PostStore
    @Inject internal lateinit var siteStore: SiteStore

    private lateinit var listDescriptor: PostListDescriptor
    private lateinit var site: SiteModel
    private var postListAdapter: PostListAdapter? = null
    private lateinit var listManager: ListManager<PostModel>
    private var refreshListDataJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.post_list_activity)

        dispatcher.register(this)
        site = siteStore.getSiteByLocalId(intent.getIntExtra(LOCAL_SITE_ID, 0))
        dispatcher.dispatch(PostActionBuilder.newRemoveAllPostsAction())
        listDescriptor = if (site.isUsingWpComRestApi) {
            PostListDescriptorForRestSite(site)
        } else {
            PostListDescriptorForXmlRpcSite(site)
        }
        runBlocking { listManager = getListDataFromStore(listDescriptor) }

        setupViews()

        listManager.refresh()
    }

    override fun onStop() {
        super.onStop()
        dispatcher.unregister(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.post_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.post_list_filter) {
            showFilterMenu()
        } else if (item.itemId == R.id.post_list_create_local_draft) {
            createLocalDraft()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showFilterMenu() {
        val view = layoutInflater.inflate(R.layout.post_list_filter_dialog, null)
        val searchEditText = view.findViewById<EditText>(R.id.post_list_filter_search_edit_text)
        val statusSpinner = view.findViewById<Spinner>(R.id.post_list_filter_status_spinner)
        val orderBySpinner = view.findViewById<Spinner>(R.id.post_list_filter_order_by_spinner)
        val orderSpinner = view.findViewById<Spinner>(R.id.post_list_filter_order_spinner)
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setView(view)
        dialogBuilder.setTitle("Filter")
        dialogBuilder.setPositiveButton("OK") { dialog, _ ->
            val selectedStatus = statusSpinner?.selectedItem.toString()
            val selectedOrderBy = PostListOrderBy.fromValue(orderBySpinner.selectedItem.toString())!!
            val selectedOrder = ListOrder.fromValue(orderSpinner.selectedItem.toString())!!
            val selectedSearchQuery = searchEditText?.text.toString()
            when (listDescriptor) {
                is PostListDescriptorForRestSite -> {
                    listDescriptor = PostListDescriptorForRestSite(
                            site = site,
                            status = PostStatusForRestSite.fromValue(selectedStatus)!!,
                            orderBy = selectedOrderBy,
                            order = selectedOrder,
                            searchQuery = selectedSearchQuery
                    )
                }
                is PostListDescriptorForXmlRpcSite -> {
                    listDescriptor = PostListDescriptorForXmlRpcSite(
                            site = site,
                            orderBy = selectedOrderBy,
                            order = selectedOrder
                    )
                }
            }
            swipeToRefresh.isRefreshing = true
            refreshListManagerFromStore(listDescriptor, true)
            dialog.dismiss()
        }
        with(listDescriptor) {
            val setupSpinnerAdapter = { spinner: Spinner, values: List<String> ->
                spinner.adapter = ArrayAdapter(
                        this@PostListActivity,
                        R.layout.support_simple_spinner_dropdown_item,
                        values
                )
            }
            setupSpinnerAdapter(orderSpinner, ListOrder.values().map { it.value })
            when (this) {
                is PostListDescriptorForRestSite -> {
                    setupSpinnerAdapter(statusSpinner, PostStatusForRestSite.values().map { it.value })
                    setupSpinnerAdapter(orderBySpinner, PostListOrderBy.values().map { it.value })

                    statusSpinner.setSelection(PostStatusForRestSite.values().indexOfFirst { it.value == status.value })
                    orderBySpinner.setSelection(PostListOrderBy.values().indexOfFirst {
                        it.value == orderBy.value
                    })
                    orderSpinner.setSelection(ListOrder.values().indexOfFirst { it.value == order.value })
                    searchEditText.setText(searchQuery)
                }
                is PostListDescriptorForXmlRpcSite -> {
                    statusSpinner.visibility = GONE
                    searchEditText.visibility = GONE
                    setupSpinnerAdapter(orderBySpinner, PostListOrderBy.values().map { it.value })

                    orderBySpinner.setSelection(PostListOrderBy.values().indexOfFirst {
                        it.value == orderBy.value
                    })
                    orderSpinner.setSelection(ListOrder.values().indexOfFirst { it.value == order.value })
                }
            }
        }
        dialogBuilder.show()
    }

    private fun setupViews() {
        recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        postListAdapter = PostListAdapter(this, listManager)
        recycler.adapter = postListAdapter

        swipeToRefresh.setOnRefreshListener {
            refreshListManagerFromStore(listDescriptor, true)
        }
    }

    private fun refreshListManagerFromStore(listDescriptor: ListDescriptor, fetchAfter: Boolean) {
        refreshListDataJob?.cancel()
        refreshListDataJob = GlobalScope.launch(Dispatchers.Main) {
            val listManager = withContext(Dispatchers.Default) { getListDataFromStore(listDescriptor) }
            if (isActive && this@PostListActivity.listDescriptor == listDescriptor) {
                val diffResult = withContext(Dispatchers.Default) {
                    DiffUtil.calculateDiff(DiffCallback(this@PostListActivity.listManager, listManager))
                }
                if (isActive && this@PostListActivity.listDescriptor == listDescriptor) {
                    updateListManager(listManager, diffResult)
                    if (fetchAfter) {
                        listManager.refresh()
                    }
                }
            }
        }
    }

    private fun updateListManager(listManager: ListManager<PostModel>, diffResult: DiffResult) {
        this.listManager = listManager
        swipeToRefresh.isRefreshing = listManager.isFetchingFirstPage
        loadingMoreProgressBar.visibility = if (listManager.isLoadingMore) View.VISIBLE else View.GONE
        postListAdapter?.setListManager(listManager, diffResult)
    }

    private fun createLocalDraft() {
        val example = PostModel()
        example.localSiteId = site.id
        example.title = "Local draft: ${Random().nextInt(1000)}"
        example.content = "Bunch of content here"
        example.setIsLocalDraft(true)
        PostSqlUtils.insertOrUpdatePost(example, false)
        refreshListManagerFromStore(listDescriptor, false)
    }

    private fun localItems(): List<PostModel>? {
        return postStore.getLocalPostsForDescriptor(listDescriptor)
    }

    private suspend fun getListDataFromStore(listDescriptor: ListDescriptor): ListManager<PostModel> =
            listStore.getListManager(listDescriptor, localItems(), object : ListItemDataSource<PostModel> {
                override fun fetchItem(listDescriptor: ListDescriptor, remoteItemId: Long) {
                    val postToFetch = PostModel()
                    postToFetch.remotePostId = remoteItemId
                    val payload = RemotePostPayload(postToFetch, site)
                    dispatcher.dispatch(PostActionBuilder.newFetchPostAction(payload))
                }

                override fun fetchList(listDescriptor: ListDescriptor, offset: Int) {
                    if (listDescriptor is PostListDescriptor) {
                        val fetchPostListPayload = FetchPostListPayload(listDescriptor, offset)
                        dispatcher.dispatch(PostActionBuilder.newFetchPostListAction(fetchPostListPayload))
                    }
                }

                override fun getItems(listDescriptor: ListDescriptor, remoteItemIds: List<Long>): Map<Long, PostModel> {
                    return postStore.getPostsByRemotePostIds(remoteItemIds, site)
                }
            })

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    @Suppress("unused")
    fun onListChanged(event: OnListChanged) {
        if (!event.listDescriptors.contains(listDescriptor)) {
            return
        }
        refreshListManagerFromStore(listDescriptor, false)
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    @Suppress("unused")
    fun onListItemsChanged(event: OnListItemsChanged) {
        if (listDescriptor.typeIdentifier != event.type) {
            return
        }
        refreshListManagerFromStore(listDescriptor, false)
    }

    companion object {
        fun newInstance(context: Context, localSiteId: Int): Intent {
            val intent = Intent(context, PostListActivity::class.java)
            intent.putExtra(LOCAL_SITE_ID, localSiteId)
            return intent
        }
    }

    private class PostListAdapter(
        context: Context,
        private var listManager: ListManager<PostModel>
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val layoutInflater = LayoutInflater.from(context)

        fun setListManager(listManager: ListManager<PostModel>, diffResult: DiffResult) {
            this.listManager = listManager
            diffResult.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.post_list_row, parent, false)
            return PostViewHolder(view)
        }

        override fun getItemCount(): Int {
            return listManager.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val postHolder = holder as PostViewHolder
            val postModel = listManager.getItem(position)
            val title = postModel?.title ?: "Loading.."
            postHolder.postTitle.text = title
        }

        private class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val postTitle: TextView = itemView.findViewById(R.id.post_list_row_post_title) as TextView
        }
    }
}

class DiffCallback(
    private val old: ListManager<PostModel>,
    private val new: ListManager<PostModel>
) : DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return ListManager.areItemsTheSame(new, old, newItemPosition, oldItemPosition) { oldItem, newItem ->
            oldItem.id == newItem.id
        }
    }

    override fun getOldListSize(): Int {
        return old.size
    }

    override fun getNewListSize(): Int {
        return new.size
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = old.getItem(oldItemPosition, false, false)
        val newItem = new.getItem(newItemPosition, false, false)
        return (oldItem == null && newItem == null) || (oldItem != null &&
                newItem != null && oldItem.title == newItem.title)
    }
}
