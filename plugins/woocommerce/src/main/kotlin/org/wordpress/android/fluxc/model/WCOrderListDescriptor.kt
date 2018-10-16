package org.wordpress.android.fluxc.model

import org.wordpress.android.fluxc.model.list.ListDescriptor
import org.wordpress.android.fluxc.model.list.ListDescriptorTypeIdentifier
import org.wordpress.android.fluxc.model.list.ListDescriptorUniqueIdentifier

class WCOrderListDescriptor(val site: SiteModel, val statusFilter: String? = null) : ListDescriptor {
    override val uniqueIdentifier: ListDescriptorUniqueIdentifier by lazy {
        ListDescriptorUniqueIdentifier("woo-site-order-list-${site.id}-sf${statusFilter.orEmpty()}".hashCode())
    }

    override val typeIdentifier: ListDescriptorTypeIdentifier by lazy {
        WCOrderListDescriptor.calculateTypeIdentifier(site.id)
    }

    companion object {
        @JvmStatic
        fun calculateTypeIdentifier(localSiteId: Int) =
                ListDescriptorTypeIdentifier("woo-site-order-list$localSiteId".hashCode())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as WCOrderListDescriptor
        return uniqueIdentifier == that.uniqueIdentifier
    }

    override fun hashCode() = uniqueIdentifier.value
}
