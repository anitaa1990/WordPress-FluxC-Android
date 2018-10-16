package org.wordpress.android.fluxc.persistence

import com.wellsql.generated.WCOrderModelTable
import com.wellsql.generated.WCOrderNoteModelTable
import com.yarolegovich.wellsql.SelectQuery
import com.yarolegovich.wellsql.WellSql
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCOrderModel
import org.wordpress.android.fluxc.model.WCOrderNoteModel
import org.wordpress.android.fluxc.model.order.OrderIdSet

object OrderSqlUtils {
    fun insertOrUpdateOrder(order: WCOrderModel): Int {
        val orderResult = WellSql.select(WCOrderModel::class.java)
                .where().beginGroup()
                .equals(WCOrderModelTable.ID, order.id)
                .or()
                .beginGroup()
                .equals(WCOrderModelTable.REMOTE_ORDER_ID, order.remoteOrderId)
                .equals(WCOrderModelTable.LOCAL_SITE_ID, order.localSiteId)
                .endGroup()
                .endGroup().endWhere()
                .asModel

        if (orderResult.isEmpty()) {
            // Insert
            WellSql.insert(order).asSingleTransaction(true).execute()
            return 1
        } else {
            // Update
            val oldId = orderResult[0].id
            return WellSql.update(WCOrderModel::class.java).whereId(oldId)
                    .put(order, UpdateAllExceptId(WCOrderModel::class.java)).execute()
        }
    }

    fun getOrderForIdSet(orderIdSet: OrderIdSet): WCOrderModel? {
        val (id, remoteOrderId, localSiteId) = orderIdSet
        return WellSql.select(WCOrderModel::class.java)
                .where().beginGroup()
                .equals(WCOrderModelTable.ID, id)
                .or()
                .beginGroup()
                .equals(WCOrderModelTable.REMOTE_ORDER_ID, remoteOrderId)
                .equals(WCOrderModelTable.LOCAL_SITE_ID, localSiteId)
                .endGroup()
                .endGroup().endWhere()
                .asModel.firstOrNull()
    }

    fun getOrdersForSite(site: SiteModel, status: List<String> = emptyList()): List<WCOrderModel> {
        val conditionClauseBuilder = WellSql.select(WCOrderModel::class.java)
                .where()
                .beginGroup()
                .equals(WCOrderModelTable.LOCAL_SITE_ID, site.id)

        if (status.isNotEmpty()) {
            conditionClauseBuilder.isIn(WCOrderModelTable.STATUS, status)
        }

        return conditionClauseBuilder.endGroup().endWhere()
                .orderBy(WCOrderModelTable.DATE_CREATED, SelectQuery.ORDER_DESCENDING)
                .asModel
    }

    fun deleteOrdersForSite(site: SiteModel): Int {
        return WellSql.delete(WCOrderModel::class.java)
                .where().beginGroup()
                .equals(WCOrderModelTable.LOCAL_SITE_ID, site.id)
                .endGroup()
                .endWhere()
                .execute()
    }

    fun deleteAllOrders() = WellSql.delete(WCOrderModel::class.java).execute()

    fun insertOrIgnoreOrderNotes(notes: List<WCOrderNoteModel>): Int {
        var totalChanged = 0
        notes.forEach { totalChanged += insertOrIgnoreOrderNote(it) }
        return totalChanged
    }

    fun insertOrIgnoreOrderNote(note: WCOrderNoteModel): Int {
        val noteResult = WellSql.select(WCOrderNoteModel::class.java)
                .where().beginGroup()
                .equals(WCOrderNoteModelTable.ID, note.id)
                .or()
                .beginGroup()
                .equals(WCOrderNoteModelTable.REMOTE_NOTE_ID, note.remoteNoteId)
                .equals(WCOrderNoteModelTable.LOCAL_SITE_ID, note.localSiteId)
                .equals(WCOrderNoteModelTable.LOCAL_ORDER_ID, note.localOrderId)
                .endGroup()
                .endGroup().endWhere()
                .asModel

        return if (noteResult.isEmpty()) {
            // Insert
            WellSql.insert(note).asSingleTransaction(true).execute()
            1
        } else {
            // Ignore
            0
        }
    }

    fun getOrderNotesForOrder(localId: Int): List<WCOrderNoteModel> =
            WellSql.select(WCOrderNoteModel::class.java)
                    .where()
                    .equals(WCOrderNoteModelTable.LOCAL_ORDER_ID, localId)
                    .endWhere()
                    .orderBy(WCOrderNoteModelTable.DATE_CREATED, SelectQuery.ORDER_DESCENDING)
                    .asModel

    fun deleteOrderNotesForSite(site: SiteModel): Int {
        return WellSql.delete(WCOrderNoteModel::class.java)
                .where()
                .equals(WCOrderNoteModelTable.LOCAL_SITE_ID, site.id)
                .endWhere()
                .execute()
    }

    fun getOrdersByRemoteIds(remoteIds: List<Long>, localSiteId: Int): List<WCOrderModel> {
        return if (remoteIds.isNotEmpty()) {
            WellSql.select(WCOrderModel::class.java)
                    .where().isIn(WCOrderModelTable.REMOTE_ORDER_ID, remoteIds)
                    .equals(WCOrderModelTable.LOCAL_SITE_ID, localSiteId).endWhere()
                    .asModel
        } else {
            emptyList()
        }
    }
}
