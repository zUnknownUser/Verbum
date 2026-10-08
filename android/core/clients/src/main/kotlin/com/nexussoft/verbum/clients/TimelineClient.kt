package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.TimelineDatePrecision
import com.nexussoft.verbum.models.TimelineEvent

/**
 * The timeline (docs/PRODUCT.md §4.2, §45 `/timeline`): curated periods and events with their
 * dating and how sure it is. Twin of iOS `TimelineClient`. Static until Task 11.
 */
interface TimelineClient {
    /** Every event, in server-provided narrative order. */
    suspend fun events(): List<TimelineEvent>
    /** The events an entity takes part in, in narrative order. */
    suspend fun eventsFor(entityId: EntityId): List<TimelineEvent>
}
