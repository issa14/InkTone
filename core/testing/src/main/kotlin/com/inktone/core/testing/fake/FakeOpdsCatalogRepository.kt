package com.inktone.core.testing.fake

import com.inktone.domain.model.OpdsCatalog
import com.inktone.domain.repository.OpdsCatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeOpdsCatalogRepository : OpdsCatalogRepository {
    private val catalogs = MutableStateFlow<List<OpdsCatalog>>(emptyList())

    override fun observeAll(): Flow<List<OpdsCatalog>> = catalogs

    override suspend fun getById(id: String): OpdsCatalog? =
        catalogs.value.firstOrNull { it.id == id }

    override suspend fun add(catalog: OpdsCatalog) {
        catalogs.value = catalogs.value.filterNot { it.id == catalog.id } + catalog
    }

    override suspend fun remove(id: String) {
        catalogs.value = catalogs.value.filterNot { it.id == id }
    }

    override suspend fun updateSearchTemplate(id: String, template: String?) {
        catalogs.value = catalogs.value.map {
            if (it.id == id) it.copy(searchTemplateUrl = template) else it
        }
    }
}
