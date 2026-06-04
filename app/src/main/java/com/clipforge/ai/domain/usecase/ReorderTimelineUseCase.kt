package com.clipforge.ai.domain.usecase
import com.clipforge.ai.domain.model.TimelineItem
class ReorderTimelineUseCase {
    operator fun invoke(items: List<TimelineItem>, fromIndex: Int, toIndex: Int): List<TimelineItem> {
        if (fromIndex == toIndex) return items
        val list = items.toMutableList()
        list.add(toIndex, list.removeAt(fromIndex))
        return list.mapIndexed { i, item -> item.copy(orderIndex = i) }
    }
}
