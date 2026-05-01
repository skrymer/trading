package com.skrymer.udgaard.backtesting.strategy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RankerFactoryMetadataTest {
  @Test
  fun `availableRankerMetadata returns one entry per registered ranker`() {
    // Given / When
    val metadata = RankerFactory.availableRankerMetadata()
    val names = RankerFactory.availableRankers()

    // Then: same set of names, and each name resolvable in the catalog
    assertEquals(names.size, metadata.size, "metadata count should match name count")
    assertEquals(names.toSet(), metadata.map { it.type }.toSet())
  }

  @Test
  fun `every metadata entry has a working create() mapping`() {
    // Given / When / Then: SectorEdge needs a sectorRanking, others don't
    RankerFactory.availableRankerMetadata().forEach { meta ->
      val instance =
        if (meta.type == "SectorEdge") {
          RankerFactory.create(
            meta.type,
            com.skrymer.udgaard.backtesting.dto
              .RankerConfig(sectorRanking = listOf("XLK"))
          )
        } else {
          RankerFactory.create(meta.type)
        }
      assertNotNull(instance, "create('${meta.type}') should not return null")
    }
  }

  @Test
  fun `only SectorEdge is Sector-Priority`() {
    // Given / When
    val byCategory = RankerFactory.availableRankerMetadata().groupBy { it.category }

    // Then
    assertEquals(listOf("SectorEdge"), byCategory["Sector-Priority"]?.map { it.type })
    assertTrue("Score-Based" in byCategory.keys)
    assertTrue("Random" in byCategory.keys)
  }

  @Test
  fun `only SectorEdge has a non-empty parameters list`() {
    // Given / When
    val withParams = RankerFactory.availableRankerMetadata().filter { it.parameters.isNotEmpty() }

    // Then
    assertEquals(listOf("SectorEdge"), withParams.map { it.type })
  }

  @Test
  fun `SectorEdge's sectorRanking parameter is required and typed stringList`() {
    // Given
    val sectorEdge = RankerFactory.availableRankerMetadata().single { it.type == "SectorEdge" }

    // When
    val sectorRanking = sectorEdge.parameters.single { it.name == "sectorRanking" }

    // Then
    assertEquals("stringList", sectorRanking.type)
    assertNull(sectorRanking.defaultValue, "null defaultValue signals required")
  }

  @Test
  fun `Random ranker is categorised as Random`() {
    // Given / When
    val random = RankerFactory.availableRankerMetadata().single { it.type == "Random" }

    // Then
    assertEquals("Random", random.category)
    assertTrue(random.usesRandomTieBreaks)
  }

  @Test
  fun `SectorEdge does not use random tie-breaks`() {
    // Given / When
    val sectorEdge = RankerFactory.availableRankerMetadata().single { it.type == "SectorEdge" }

    // Then
    assertEquals(false, sectorEdge.usesRandomTieBreaks)
  }
}
