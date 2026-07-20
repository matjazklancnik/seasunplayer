package com.example.shazamytdl.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SongRecognitionClientTest {
    @Test
    fun parsesSimpleRecognitionResponse() {
        val result = SongRecognitionClient.parseResponse(
            """
            {
              "artist": "Adele",
              "title": "Hello",
              "youtubeVideoId": "YQHsXMglC9A",
              "score": 99
            }
            """.trimIndent()
        )

        assertEquals("Adele", result.artist)
        assertEquals("Hello", result.title)
        assertEquals("YQHsXMglC9A", result.youtubeVideoId)
        assertEquals(99.0, result.score ?: -1.0, 0.0)
    }

    @Test
    fun parsesAcrCloudMusicResponse() {
        val result = SongRecognitionClient.parseResponse(
            """
            {
              "status": { "code": 0, "msg": "Success" },
              "metadata": {
                "music": [
                  {
                    "title": "Second",
                    "artists": [{ "name": "Lower Score" }],
                    "score": 72
                  },
                  {
                    "title": "Hello",
                    "artists": [{ "name": "Adele" }],
                    "score": 100,
                    "external_metadata": {
                      "youtube": { "vid": "YQHsXMglC9A" }
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals("Adele", result.artist)
        assertEquals("Hello", result.title)
        assertEquals("YQHsXMglC9A", result.youtubeVideoId)
        assertEquals(100.0, result.score ?: -1.0, 0.0)
    }

    @Test
    fun parsesAudDRecognitionResponse() {
        val result = SongRecognitionClient.parseResponse(
            """
            {
              "status": "success",
              "result": {
                "artist": "Imagine Dragons",
                "title": "Warriors",
                "album": "Warriors",
                "song_link": "https://lis.tn/Warriors"
              }
            }
            """.trimIndent()
        )

        assertEquals("Imagine Dragons", result.artist)
        assertEquals("Warriors", result.title)
    }

    @Test
    fun throwsForAudDNoResult() {
        assertThrows(SongRecognitionException::class.java) {
            SongRecognitionClient.parseResponse(
                """
                {
                  "status": "success",
                  "result": null
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun throwsForAudDQuotaError() {
        assertThrows(SongRecognitionException::class.java) {
            SongRecognitionClient.parseResponse(
                """
                {
                  "status": "error",
                  "error": {
                    "error_code": 901,
                    "error_message": "No api_token passed, and the limit was reached."
                  }
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun throwsForNoRecognitionStatus() {
        assertThrows(SongRecognitionException::class.java) {
            SongRecognitionClient.parseResponse(
                """
                {
                  "status": { "code": 1001, "msg": "No result" }
                }
                """.trimIndent()
            )
        }
    }
}
