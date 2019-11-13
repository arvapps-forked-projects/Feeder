package com.nononsenseapps.feeder.db.room

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.nononsenseapps.feeder.FeederApplication
import com.nononsenseapps.feeder.db.legacy.*
import com.nononsenseapps.feeder.util.contentValues
import com.nononsenseapps.feeder.util.setInt
import com.nononsenseapps.feeder.util.setLong
import com.nononsenseapps.feeder.util.setString
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL

@RunWith(AndroidJUnit4::class)
@LargeTest
class MigrationFromLegacy5ToLatest {

    private val feederApplication: FeederApplication = getApplicationContext()

    @Rule
    @JvmField
    val testHelper: MigrationTestHelper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory())

    private val testDbName = "TestingDatabase"

    private val legacyDb: LegacyDatabaseHandler
        get() = LegacyDatabaseHandler(context = feederApplication,
                name = testDbName,
                version = 5)

    private val roomDb: AppDatabase
        get() =
            Room.databaseBuilder(feederApplication,
                    AppDatabase::class.java,
                    testDbName)
                    .addMigrations(*allMigrations)
                    .build().also { testHelper.closeWhenFinished(it) }

    @Before
    fun setup() {
        legacyDb.writableDatabase.use { db ->
            db.execSQL("""
                CREATE TABLE $FEED_TABLE_NAME (
                  $COL_ID INTEGER PRIMARY KEY,
                  $COL_TITLE TEXT NOT NULL,
                  $COL_CUSTOM_TITLE TEXT NOT NULL,
                  $COL_URL TEXT NOT NULL,
                  $COL_TAG TEXT NOT NULL DEFAULT '',
                  $COL_NOTIFY INTEGER NOT NULL DEFAULT 0,
                  UNIQUE($COL_URL) ON CONFLICT REPLACE
                )""")
            db.execSQL(CREATE_FEED_ITEM_TABLE)
            db.execSQL(CREATE_TAG_TRIGGER)
            db.execSQL("""
                CREATE TEMP VIEW IF NOT EXISTS WithUnreadCount
                AS SELECT $COL_ID, $COL_TITLE, $COL_URL, $COL_TAG, $COL_CUSTOM_TITLE, $COL_NOTIFY, "unreadcount"
                   FROM $FEED_TABLE_NAME
                   LEFT JOIN (SELECT COUNT(1) AS ${"unreadcount"}, $COL_FEED
                     FROM $FEED_ITEM_TABLE_NAME
                     WHERE $COL_UNREAD IS 1
                     GROUP BY $COL_FEED)
                   ON $FEED_TABLE_NAME.$COL_ID = $COL_FEED""")
            db.execSQL(CREATE_TAGS_VIEW)

            // Bare minimum non-null feeds
            val idA = db.insert(FEED_TABLE_NAME, null, contentValues {
                setString(COL_TITLE to "feedA")
                setString(COL_CUSTOM_TITLE to "feedACustom")
                setString(COL_URL to "https://feedA")
                setString(COL_TAG to "")
            })

            // All fields filled
            val idB = db.insert(FEED_TABLE_NAME, null, contentValues {
                setString(COL_TITLE to "feedB")
                setString(COL_CUSTOM_TITLE to "feedBCustom")
                setString(COL_URL to "https://feedB")
                setString(COL_TAG to "tag")
                setInt(COL_NOTIFY to 1)
            })

            IntRange(0, 1).forEach { index ->
                db.insert(FEED_ITEM_TABLE_NAME, null, contentValues {
                    setLong(COL_FEED to idA)
                    setString(COL_GUID to "guid$index")
                    setString(COL_TITLE to "title$index")
                    setString(COL_DESCRIPTION to "desc$index")
                    setString(COL_PLAINTITLE to "plain$index")
                    setString(COL_PLAINSNIPPET to "snippet$index")
                    setString(COL_FEEDTITLE to "feedA")
                    setString(COL_FEEDURL to "https://feedA")
                    setString(COL_TAG to "")
                })

                db.insert(FEED_ITEM_TABLE_NAME, null, contentValues {
                    setLong(COL_FEED to idB)
                    setString(COL_GUID to "guid$index")
                    setString(COL_TITLE to "title$index")
                    setString(COL_DESCRIPTION to "desc$index")
                    setString(COL_PLAINTITLE to "plain$index")
                    setString(COL_PLAINSNIPPET to "snippet$index")
                    setString(COL_FEEDTITLE to "feedB")
                    setString(COL_FEEDURL to "https://feedB")
                    setString(COL_TAG to "tag")
                    setInt(COL_NOTIFIED to 1)
                    setInt(COL_UNREAD to 0)
                    setString(COL_AUTHOR to "author$index")
                    setString(COL_ENCLOSURELINK to "https://enclosure$index")
                    setString(COL_IMAGEURL to "https://image$index")
                    setString(COL_PUBDATE to DateTime(2018, 2, 3, 4, 5).toString())
                    setString(COL_LINK to "https://link$index")
                })
            }
        }
    }

    @After
    fun tearDown() {
        assertTrue(feederApplication.deleteDatabase(testDbName))
    }

    @Test
    fun legacyMigrationTo7MinimalFeed() = runBlocking {
        testHelper.runMigrationsAndValidate(testDbName, 7, true,
                MIGRATION_5_7, MIGRATION_7_8)

        roomDb.let { db ->
            val feeds = db.feedDao().loadFeeds()

            assertEquals("Wrong number of feeds", 2, feeds.size)

            val feedA = feeds[0]

            assertEquals("feedA", feedA.title)
            assertEquals("feedACustom", feedA.customTitle)
            assertEquals(URL("https://feedA"), feedA.url)
            assertEquals("", feedA.tag)
            assertEquals(DateTime(0, DateTimeZone.UTC), feedA.lastSync)
            assertFalse(feedA.notify)
            assertNull(feedA.imageUrl)
        }
    }

    @Test
    fun legacyMigrationTo7CompleteFeed() = runBlocking {
        testHelper.runMigrationsAndValidate(testDbName, 7, true,
                MIGRATION_5_7, MIGRATION_7_8)

        roomDb.let { db ->
            val feeds = db.feedDao().loadFeeds()

            assertEquals("Wrong number of feeds", 2, feeds.size)

            val feedB = feeds[1]

            assertEquals("feedB", feedB.title)
            assertEquals("feedBCustom", feedB.customTitle)
            assertEquals(URL("https://feedB"), feedB.url)
            assertEquals("tag", feedB.tag)
            assertEquals(DateTime(0, DateTimeZone.UTC), feedB.lastSync)
            assertTrue(feedB.notify)
            assertNull(feedB.imageUrl)
        }
    }

    @Test
    fun legacyMigrationTo7MinimalFeedItem() = runBlocking {
        testHelper.runMigrationsAndValidate(testDbName, 7, true,
                MIGRATION_5_7, MIGRATION_7_8)

        roomDb.let { db ->
            val feed = db.feedDao().loadFeeds()[0]
            assertEquals("feedA", feed.title)
            val items = db.feedItemDao().loadFeedItemsInFeed(feedId = feed.id)

            assertEquals(2, items.size)

            items.forEachIndexed { index, it ->
                assertEquals(feed.id, it.feedId)
                assertEquals("guid$index", it.guid)
                assertEquals("title$index", it.title)
                assertEquals("desc$index", it.description)
                assertEquals("plain$index", it.plainTitle)
                assertEquals("snippet$index", it.plainSnippet)
                assertTrue(it.unread)
                assertNull(it.author)
                assertNull(it.enclosureLink)
                assertNull(it.imageUrl)
                assertNull(it.pubDate)
                assertNull(it.link)
                assertFalse(it.notified)
            }
        }
    }

    @Test
    fun legacyMigrationTo7CompleteFeedItem() = runBlocking {
        testHelper.runMigrationsAndValidate(testDbName, 7, true,
                MIGRATION_5_7, MIGRATION_7_8)

        roomDb.let { db ->
            val feed = db.feedDao().loadFeeds()[1]
            assertEquals("feedB", feed.title)
            val items = db.feedItemDao().loadFeedItemsInFeed(feedId = feed.id)

            assertEquals(2, items.size)

            items.forEachIndexed { index, it ->
                assertEquals(feed.id, it.feedId)
                assertEquals("guid$index", it.guid)
                assertEquals("title$index", it.title)
                assertEquals("desc$index", it.description)
                assertEquals("plain$index", it.plainTitle)
                assertEquals("snippet$index", it.plainSnippet)
                assertFalse(it.unread)
                assertEquals("author$index", it.author)
                assertEquals("https://enclosure$index", it.enclosureLink)
                assertEquals("https://image$index", it.imageUrl)
                assertEquals(DateTime(2018, 2, 3, 4, 5).toString(), it.pubDate.toString())
                assertEquals("https://link$index", it.link)
                assertTrue(it.notified)
            }
        }
    }
}
