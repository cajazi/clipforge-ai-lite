package com.clipforge.ai.validation.c9.support

import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicInteger

/**
 * C9.0 support harness: decorates a real [EffectRepository] and counts writes, so
 * [com.clipforge.ai.validation.c9.AnimationDraftWorkflowTest] can assert zero writes during a
 * draft, exactly one history entry on confirm, and zero on cancel. Delegates every read/write to
 * the wrapped repository unchanged - it observes, it does not alter persistence behavior.
 */
class RepositorySpy(private val delegate: EffectRepository) : EffectRepository {
    private val upsertCount = AtomicInteger(0)
    private val deleteCount = AtomicInteger(0)
    private val deleteForProjectCount = AtomicInteger(0)

    val writeCount: Int get() = upsertCount.get() + deleteCount.get() + deleteForProjectCount.get()

    fun resetCounts() {
        upsertCount.set(0)
        deleteCount.set(0)
        deleteForProjectCount.set(0)
    }

    override suspend fun getEffectsForProject(projectId: String): List<EffectItem> =
        delegate.getEffectsForProject(projectId)

    override fun observeEffectsForProject(projectId: String): Flow<List<EffectItem>> =
        delegate.observeEffectsForProject(projectId)

    override suspend fun upsertEffect(effect: EffectItem) {
        upsertCount.incrementAndGet()
        delegate.upsertEffect(effect)
    }

    override suspend fun deleteEffect(id: String) {
        deleteCount.incrementAndGet()
        delegate.deleteEffect(id)
    }

    override suspend fun deleteEffectsForProject(projectId: String) {
        deleteForProjectCount.incrementAndGet()
        delegate.deleteEffectsForProject(projectId)
    }
}
