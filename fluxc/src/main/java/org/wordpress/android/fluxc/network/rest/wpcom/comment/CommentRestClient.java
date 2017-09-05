package org.wordpress.android.fluxc.network.rest.wpcom.comment;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.android.volley.RequestQueue;
import com.android.volley.Response.Listener;

import org.apache.commons.text.StringEscapeUtils;
import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.RequestPayload;
import org.wordpress.android.fluxc.generated.CommentActionBuilder;
import org.wordpress.android.fluxc.generated.endpoint.WPCOMREST;
import org.wordpress.android.fluxc.model.CommentModel;
import org.wordpress.android.fluxc.model.PostModel;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.network.BaseRequest.BaseErrorListener;
import org.wordpress.android.fluxc.network.BaseRequest.BaseNetworkError;
import org.wordpress.android.fluxc.network.UserAgent;
import org.wordpress.android.fluxc.network.rest.wpcom.BaseWPComRestClient;
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest;
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken;
import org.wordpress.android.fluxc.network.rest.wpcom.comment.CommentWPComRestResponse.CommentsWPComRestResponse;
import org.wordpress.android.fluxc.store.CommentStore.FetchCommentsPayload;
import org.wordpress.android.fluxc.store.CommentStore.FetchCommentsResponsePayload;
import org.wordpress.android.fluxc.store.CommentStore.RemoteCommentPayload;
import org.wordpress.android.fluxc.store.CommentStore.RemoteCommentResponsePayload;
import org.wordpress.android.fluxc.utils.CommentErrorUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

public class CommentRestClient extends BaseWPComRestClient {
    @Inject
    public CommentRestClient(Context appContext, Dispatcher dispatcher, RequestQueue requestQueue,
                             AccessToken accessToken, UserAgent userAgent) {
        super(appContext, dispatcher, requestQueue, accessToken, userAgent);
    }

    public void fetchComments(final FetchCommentsPayload fetchCommentsPayload) {
        String url = WPCOMREST.sites.site(fetchCommentsPayload.site.getSiteId()).comments.getUrlV1_1();
        Map<String, String> params = new HashMap<>();
        params.put("status", fetchCommentsPayload.status.toString());
        params.put("offset", String.valueOf(fetchCommentsPayload.offset));
        params.put("number", String.valueOf(fetchCommentsPayload.number));
        final WPComGsonRequest<CommentsWPComRestResponse> request = WPComGsonRequest.buildGetRequest(
                url, params, CommentsWPComRestResponse.class,
                new Listener<CommentsWPComRestResponse>() {
                    @Override
                    public void onResponse(CommentsWPComRestResponse response) {
                        List<CommentModel> comments = commentsResponseToCommentList(response, fetchCommentsPayload.site);
                        FetchCommentsResponsePayload payload = new FetchCommentsResponsePayload(fetchCommentsPayload,
                                comments, fetchCommentsPayload.site, fetchCommentsPayload.number,
                                fetchCommentsPayload.offset);
                        mDispatcher.dispatch(CommentActionBuilder.newFetchedCommentsAction(payload));
                    }
                },

                new BaseErrorListener() {
                    @Override
                    public void onErrorResponse(@NonNull BaseNetworkError error) {
                        mDispatcher.dispatch(CommentActionBuilder.newFetchedCommentsAction(
                                CommentErrorUtils.commentErrorToFetchCommentsPayload(fetchCommentsPayload, error,
                                        fetchCommentsPayload.site)));
                    }
                }
        );
        add(fetchCommentsPayload, request);
    }

    public void pushComment(final RequestPayload requestPayload, final SiteModel site,
                            @NonNull final CommentModel comment) {
        String url = WPCOMREST.sites.site(site.getSiteId()).comments.comment(comment.getRemoteCommentId()).getUrlV1_1();
        Map<String, Object> params = new HashMap<>();
        params.put("content", comment.getContent());
        params.put("date", comment.getDatePublished());
        params.put("status", comment.getStatus());
        final WPComGsonRequest<CommentWPComRestResponse> request = WPComGsonRequest.buildPostRequest(
                url, params, CommentWPComRestResponse.class,
                new Listener<CommentWPComRestResponse>() {
                    @Override
                    public void onResponse(CommentWPComRestResponse response) {
                        CommentModel newComment = commentResponseToComment(response, site);
                        newComment.setId(comment.getId()); // reconciliate local instance and newly created object
                        RemoteCommentResponsePayload payload = new RemoteCommentResponsePayload(requestPayload,
                                newComment);
                        mDispatcher.dispatch(CommentActionBuilder.newPushedCommentAction(payload));
                    }
                },

                new BaseErrorListener() {
                    @Override
                    public void onErrorResponse(@NonNull BaseNetworkError error) {
                        mDispatcher.dispatch(CommentActionBuilder.newPushedCommentAction(
                                CommentErrorUtils.commentErrorToPushCommentPayload(requestPayload, error, comment)));
                    }
                }
        );
        add(requestPayload, request);
    }

    public void fetchComment(final RemoteCommentPayload remoteCommentPayload) {
        long remoteCommentId = remoteCommentPayload.remoteCommentId;

        // Prioritize CommentModel over comment id.
        if (remoteCommentPayload.comment != null) {
            remoteCommentId = remoteCommentPayload.comment.getRemoteCommentId();
        }

        String url = WPCOMREST.sites.site(remoteCommentPayload.site.getSiteId()).comments.comment(remoteCommentId)
                .getUrlV1_1();
        final WPComGsonRequest<CommentWPComRestResponse> request = WPComGsonRequest.buildGetRequest(
                url, null, CommentWPComRestResponse.class,
                new Listener<CommentWPComRestResponse>() {
                    @Override
                    public void onResponse(CommentWPComRestResponse response) {
                        CommentModel comment = commentResponseToComment(response, remoteCommentPayload.site);
                        RemoteCommentResponsePayload payload =
                                new RemoteCommentResponsePayload(remoteCommentPayload, remoteCommentPayload.comment);
                        mDispatcher.dispatch(CommentActionBuilder.newFetchedCommentAction(payload));
                    }
                },

                new BaseErrorListener() {
                    @Override
                    public void onErrorResponse(@NonNull BaseNetworkError error) {
                        mDispatcher.dispatch(CommentActionBuilder.newFetchedCommentAction(
                                CommentErrorUtils.commentErrorToFetchCommentPayload(remoteCommentPayload, error,
                                        remoteCommentPayload.comment)));
                    }
                }
        );
        add(remoteCommentPayload, request);
    }

    public void deleteComment(final RequestPayload requestPayload, final SiteModel site, long remoteCommentId,
                              @Nullable final CommentModel comment) {
        // Prioritize CommentModel over comment id.
        if (comment != null) {
            remoteCommentId = comment.getRemoteCommentId();
        }

        String url = WPCOMREST.sites.site(site.getSiteId()).comments.comment(remoteCommentId).delete.getUrlV1_1();
        final WPComGsonRequest<CommentWPComRestResponse> request = WPComGsonRequest.buildPostRequest(
                url, null, CommentWPComRestResponse.class,
                new Listener<CommentWPComRestResponse>() {
                    @Override
                    public void onResponse(CommentWPComRestResponse response) {
                        CommentModel modifiedComment = commentResponseToComment(response, site);
                        if (comment != null) {
                            // reconciliate local instance and newly created object if it exists locally
                            modifiedComment.setId(comment.getId());
                        }
                        RemoteCommentResponsePayload payload = new RemoteCommentResponsePayload(requestPayload,
                                modifiedComment);
                        mDispatcher.dispatch(CommentActionBuilder.newDeletedCommentAction(payload));
                    }
                },

                new BaseErrorListener() {
                    @Override
                    public void onErrorResponse(@NonNull BaseNetworkError error) {
                        mDispatcher.dispatch(CommentActionBuilder.newDeletedCommentAction(
                                CommentErrorUtils.commentErrorToFetchCommentPayload(requestPayload, error, comment)));
                    }
                }
        );
        add(requestPayload, request);
    }

    public void createNewReply(final RequestPayload requestPayload, final SiteModel site, final CommentModel comment,
                               final CommentModel reply) {
        String url = WPCOMREST.sites.site(site.getSiteId()).comments.comment(comment.getRemoteCommentId())
                .replies.new_.getUrlV1_1();
        Map<String, Object> params = new HashMap<>();
        params.put("content", reply.getContent());
        final WPComGsonRequest<CommentWPComRestResponse> request = WPComGsonRequest.buildPostRequest(
                url, params, CommentWPComRestResponse.class,
                new Listener<CommentWPComRestResponse>() {
                    @Override
                    public void onResponse(CommentWPComRestResponse response) {
                        CommentModel newComment = commentResponseToComment(response, site);
                        newComment.setId(reply.getId()); // reconciliate local instance and newly created object
                        RemoteCommentResponsePayload payload = new RemoteCommentResponsePayload(requestPayload,
                                newComment);
                        mDispatcher.dispatch(CommentActionBuilder.newCreatedNewCommentAction(payload));
                    }
                },

                new BaseErrorListener() {
                    @Override
                    public void onErrorResponse(@NonNull BaseNetworkError error) {
                        mDispatcher.dispatch(CommentActionBuilder.newCreatedNewCommentAction(
                                CommentErrorUtils.commentErrorToFetchCommentPayload(requestPayload, error, reply)));
                    }
                }
        );
        add(requestPayload, request);
    }

    public void createNewComment(final RequestPayload requestPayload, final SiteModel site, final PostModel post,
                                 final CommentModel comment) {
        String url = WPCOMREST.sites.site(site.getSiteId()).posts.post(post.getRemotePostId())
                .replies.new_.getUrlV1_1();
        Map<String, Object> params = new HashMap<>();
        params.put("content", comment.getContent());
        final WPComGsonRequest<CommentWPComRestResponse> request = WPComGsonRequest.buildPostRequest(
                url, params, CommentWPComRestResponse.class,
                new Listener<CommentWPComRestResponse>() {
                    @Override
                    public void onResponse(CommentWPComRestResponse response) {
                        CommentModel newComment = commentResponseToComment(response, site);
                        newComment.setId(comment.getId()); // reconciliate local instance and newly created object
                        RemoteCommentResponsePayload payload = new RemoteCommentResponsePayload(requestPayload,
                                newComment);
                        mDispatcher.dispatch(CommentActionBuilder.newCreatedNewCommentAction(payload));
                    }
                },

                new BaseErrorListener() {
                    @Override
                    public void onErrorResponse(@NonNull BaseNetworkError error) {
                        mDispatcher.dispatch(CommentActionBuilder.newCreatedNewCommentAction(
                                CommentErrorUtils.commentErrorToFetchCommentPayload(requestPayload, error,
                                        comment)));
                    }
                }
        );
        add(requestPayload, request);
    }

    public void likeComment(final RequestPayload requestPayload, final SiteModel site, long remoteCommentId,
                            @Nullable final CommentModel comment, boolean like) {
        // Prioritize CommentModel over comment id.
        if (comment != null) {
            remoteCommentId = comment.getRemoteCommentId();
        }

        String url;
        if (like) {
            url = WPCOMREST.sites.site(site.getSiteId()).comments.comment(remoteCommentId).likes.new_.getUrlV1_1();
        } else {
            url = WPCOMREST.sites.site(site.getSiteId()).comments.comment(remoteCommentId).likes.mine.delete
                    .getUrlV1_1();
        }
        final WPComGsonRequest<CommentLikeWPComRestResponse> request = WPComGsonRequest.buildPostRequest(
                url, null, CommentLikeWPComRestResponse.class,
                new Listener<CommentLikeWPComRestResponse>() {
                    @Override
                    public void onResponse(CommentLikeWPComRestResponse response) {
                        RemoteCommentResponsePayload payload = new RemoteCommentResponsePayload(requestPayload, comment);

                        if (comment != null) {
                            comment.setILike(response.i_like);
                        }
                        mDispatcher.dispatch(CommentActionBuilder.newLikedCommentAction(payload));
                    }
                },

                new BaseErrorListener() {
                    @Override
                    public void onErrorResponse(@NonNull BaseNetworkError error) {
                        mDispatcher.dispatch(CommentActionBuilder.newLikedCommentAction(
                                CommentErrorUtils.commentErrorToFetchCommentPayload(requestPayload, error, comment)));
                    }
                }
        );
        add(requestPayload, request);
    }

    // Private methods

    private List<CommentModel> commentsResponseToCommentList(CommentsWPComRestResponse response, SiteModel site) {
        List<CommentModel> comments = new ArrayList<>();
        if (response.comments != null) {
            for (CommentWPComRestResponse restComment : response.comments) {
                comments.add(commentResponseToComment(restComment, site));
            }
        }
        return comments;
    }

    private CommentModel commentResponseToComment(CommentWPComRestResponse response, SiteModel site) {
        CommentModel comment = new CommentModel();

        comment.setRemoteCommentId(response.ID);
        comment.setLocalSiteId(site.getId());
        comment.setRemoteSiteId(site.getSiteId());

        comment.setStatus(response.status);
        comment.setDatePublished(response.date);
        comment.setContent(response.content);
        comment.setILike(response.i_like);

        if (response.author != null) {
            comment.setAuthorUrl(response.author.URL);
            comment.setAuthorName(StringEscapeUtils.unescapeHtml4(response.author.name));
            if ("false".equals(response.author.email)) {
                comment.setAuthorEmail("");
            } else {
                comment.setAuthorEmail(response.author.email);
            }
            comment.setAuthorProfileImageUrl(response.author.avatar_URL);
        }

        if (response.post != null) {
            comment.setRemotePostId(response.post.ID);
            comment.setPostTitle(StringEscapeUtils.unescapeHtml4(response.post.title));
        }

        if (response.author != null) {
            comment.setRemoteParentCommentId(response.author.ID);
        }

        return comment;
    }
}
