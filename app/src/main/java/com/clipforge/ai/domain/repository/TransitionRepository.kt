package com.clipforge.ai.domain.repository

import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.Transition
import com.clipforge.ai.domain.model.TransitionType

interface TransitionRepository {
    /** Save transition for one specific clip */
    suspend fun saveTransition(projectId: String, clipId: String, transition: Transition): NetworkResult<Unit>
    /** Save same transition for ALL clips in project */
    suspend fun saveAllTransitions(projectId: String, transition: Transition): NetworkResult<Unit>
}
