import psycopg

from verbum_pipeline.publish import publish
from verbum_pipeline.review import prepare

from .conftest import ROOT, approve_for_test

MIGRATION = (ROOT / "backend/db/migrations/0013_retire_demonstration_evidence.sql").read_text()


def test_withdrawal_preserves_identity_scripture_and_later_editorial_work(
    database, artifacts, tmp_path
):
    source, _, decisions = artifacts
    publish(
        source,
        approve_for_test(decisions, tmp_path / "approved.json"),
        database,
        allow_fixtures=True,
    )
    with psycopg.connect(database) as conn:
        before = conn.execute("SELECT id,type,name,summary FROM entities ORDER BY id").fetchall()
        passages = conn.execute(
            "SELECT * FROM entity_key_passages ORDER BY entity_id,position"
        ).fetchall()
        conn.execute("""
            INSERT INTO sources(id,citation) VALUES
            ('verbum.localization.pt-BR','Translation'),('editorial.history','Later historian');
            INSERT INTO entity_localizations(entity_id,language,source_id,name,fields) VALUES
            ('fixture.person.david','pt-BR','verbum.localization.pt-BR','Davi',
             '{"approximateDates":"c. 1010–970 a.C. (datado comumente; a cronologia é aproximada)",
              "role":"Rei"}'),
            ('fixture.place.elah','pt-BR','verbum.localization.pt-BR','Vale de Elá',
             '{"modernGeography":"Wadi es-Sunt, Israel (comumente identificado)"}');
            INSERT INTO entity_detail_sources(entity_id,source_id) VALUES
            ('fixture.person.solomon','editorial.history');
            UPDATE entity_details SET approximate_dates='Later edited date'
            WHERE entity_id='fixture.person.paul';
        """)
        unsupported = {
            r[0]
            for r in conn.execute("""SELECT relationship_id FROM relationship_sources
            GROUP BY relationship_id
            HAVING array_agg(source_id)=ARRAY['fixture.source.editorial']""")
        }
        assert unsupported  # Exercise actual legacy edges, not only a synthetic example.
        for _ in range(2):
            conn.execute(MIGRATION)
            assert (
                conn.execute("SELECT id,type,name,summary FROM entities ORDER BY id").fetchall()
                == before
            )
            assert (
                conn.execute(
                    "SELECT * FROM entity_key_passages ORDER BY entity_id,position"
                ).fetchall()
                == passages
            )
            for table in (
                "entity_sources",
                "entity_detail_sources",
                "relationship_sources",
                "timeline_event_sources",
            ):
                assert (
                    conn.execute(
                        f"SELECT count(*) FROM {table} WHERE source_id='fixture.source.editorial'"
                    ).fetchone()[0]
                    == 0
                )
            assert (
                conn.execute(
                    "SELECT approximate_dates FROM entity_details "
                    "WHERE entity_id='fixture.person.david'"
                ).fetchone()[0]
                is None
            )
            assert (
                conn.execute(
                    "SELECT modern_geography FROM entity_details "
                    "WHERE entity_id='fixture.place.elah'"
                ).fetchone()[0]
                is None
            )
            assert conn.execute(
                "SELECT fields FROM entity_localizations WHERE entity_id='fixture.person.david'"
            ).fetchone()[0] == {"approximateDates": None, "role": "Rei"}
            assert (
                conn.execute(
                    "SELECT fields->>'modernGeography' FROM entity_localizations "
                    "WHERE entity_id='fixture.place.elah'"
                ).fetchone()[0]
                is None
            )
            assert (
                conn.execute(
                    "SELECT approximate_dates FROM entity_details "
                    "WHERE entity_id='fixture.person.paul'"
                ).fetchone()[0]
                == "Later edited date"
            )
            assert (
                conn.execute(
                    "SELECT approximate_dates FROM entity_details "
                    "WHERE entity_id='fixture.person.solomon'"
                ).fetchone()[0]
                == "c. 970–931 BC (commonly dated)"
            )
            assert not unsupported & {r[0] for r in conn.execute("SELECT id FROM relationships")}
            assert (
                conn.execute(
                    "SELECT count(*) FROM entity_detail_sources "
                    "WHERE source_id='fixture.source.web'"
                ).fetchone()[0]
                == 25
            )
            assert conn.execute("SELECT count(*) FROM content_publications").fetchone()[0] == 1


def test_current_bilingual_catalogues_survive_withdrawal(database, artifacts, tmp_path):
    source, _, decisions = artifacts
    publish(
        source,
        approve_for_test(decisions, tmp_path / "fixtures.json"),
        database,
        allow_fixtures=True,
    )
    for index, path in enumerate(
        (
            "themes/catalog.json",
            "timeline/catalog.json",
            "events/catalog.json",
            "sequences/exodus.json",
        )
    ):
        source = ROOT / "pipeline/sources" / path
        queue, decisions, approved = [
            tmp_path / f"{index}-{suffix}.json" for suffix in ("q", "d", "a")
        ]
        prepare(source, queue, decisions)
        publish(source, approve_for_test(decisions, approved), database)
    with psycopg.connect(database) as conn:
        tables = (
            "timeline_events",
            "event_sequence_items",
            "timeline_event_catalog",
            "entity_localizations",
            "entity_key_passages",
        )
        before = {
            table: conn.execute(
                f"SELECT row_to_json(t)::text FROM {table} t ORDER BY row_to_json(t)::text"
            ).fetchall()
            for table in tables
        }
        conn.execute(MIGRATION)
        for table in tables:
            assert (
                conn.execute(
                    f"SELECT row_to_json(t)::text FROM {table} t ORDER BY row_to_json(t)::text"
                ).fetchall()
                == before[table]
            )
        assert (
            conn.execute(
                "SELECT count(*) FROM source_localizations WHERE language='pt-BR'"
            ).fetchone()[0]
            == 3
        )
        assert (
            conn.execute(
                "SELECT count(*) FROM source_localizations WHERE language='en'"
            ).fetchone()[0]
            == 3
        )
        conn.execute(
            "UPDATE source_localizations SET fields=jsonb_build_object('citation',%s::text) "
            "WHERE reference_id='editorial.events.2026-10' AND language='en'",
            ("Later reviewed attribution",),
        )
        conn.execute(MIGRATION)
        assert (
            conn.execute(
                "SELECT fields->>'citation' FROM source_localizations "
                "WHERE reference_id='editorial.events.2026-10' AND language='en'"
            ).fetchone()[0]
            == "Later reviewed attribution"
        )
