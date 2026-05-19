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
    // Given / When / Then: Sector-Priority rankers need a sectorRanking, others don't
    val sampleSectorRanking = com.skrymer.udgaard.backtesting.dto
      .RankerConfig(sectorRanking = listOf("XLK"))
    RankerFactory.availableRankerMetadata().forEach { meta ->
      val instance =
        if (meta.category == "Sector-Priority") {
          RankerFactory.create(meta.type, sampleSectorRanking)
        } else {
          RankerFactory.create(meta.type)
        }
      assertNotNull(instance, "create('${meta.type}') should not return null")
    }
  }

  @Test
  fun `every Sector-Priority ranker requires a sectorRanking parameter`() {
    // Given / When
    val sectorPriority = RankerFactory.availableRankerMetadata().filter { it.category == "Sector-Priority" }

    // Then: ≥1 Sector-Priority ranker exists, and every one declares the same required parameter shape
    assertTrue(sectorPriority.isNotEmpty(), "expected at least one Sector-Priority ranker")
    sectorPriority.forEach { meta ->
      val sectorRanking = meta.parameters.singleOrNull { it.name == "sectorRanking" }
      assertNotNull(sectorRanking, "${meta.type} should declare a sectorRanking parameter")
      assertEquals("stringList", sectorRanking!!.type)
      assertNull(sectorRanking.defaultValue, "${meta.type}.sectorRanking should be required (null default)")
    }
    // Score-Based and Random categories also exist alongside Sector-Priority
    val byCategory = RankerFactory.availableRankerMetadata().groupBy { it.category }
    assertTrue("Score-Based" in byCategory.keys)
    assertTrue("Random" in byCategory.keys)
  }

  @Test
  fun `every ranker with parameters is in the Sector-Priority category`() {
    // Given / When
    val withParams = RankerFactory.availableRankerMetadata().filter { it.parameters.isNotEmpty() }

    // Then: parameters are currently only used by Sector-Priority rankers — Score-Based and
    // Random rankers are parameter-less by design
    assertTrue(withParams.isNotEmpty())
    withParams.forEach { meta ->
      assertEquals("Sector-Priority", meta.category, "${meta.type} has parameters but isn't Sector-Priority")
    }
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

  @Test
  fun `catalog exposes SectorEdgeWithTightness as a Sector-Priority ranker with required sectorRanking`() {
    // Given / When
    val meta = RankerFactory.availableRankerMetadata().single { it.type == "SectorEdgeWithTightness" }

    // Then: same shape as SectorEdge — Sector-Priority, deterministic tie-breaks, required sectorRanking
    assertEquals("Sector-Priority", meta.category)
    assertEquals(false, meta.usesRandomTieBreaks)
    val sectorRanking = meta.parameters.single { it.name == "sectorRanking" }
    assertEquals("stringList", sectorRanking.type)
    assertNull(sectorRanking.defaultValue, "sectorRanking should be required")
  }

  @Test
  fun `create resolves SectorEdgeWithTightness to a SectorEdgeWithTightnessRanker`() {
    // Given: a Sector-Priority ranker request with the canonical sectorRanking config
    val config = com.skrymer.udgaard.backtesting.dto
      .RankerConfig(sectorRanking = listOf("XLK", "XLF", "XLE"))

    // When
    val ranker = RankerFactory.create("SectorEdgeWithTightness", config)

    // Then
    assertNotNull(ranker, "factory should resolve SectorEdgeWithTightness")
    assertTrue(
      ranker is SectorEdgeWithTightnessRanker,
      "expected SectorEdgeWithTightnessRanker, got ${ranker?.javaClass?.simpleName}",
    )
  }
}
