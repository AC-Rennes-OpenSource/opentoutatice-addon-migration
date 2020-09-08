CREATE OR REPLACE FUNCTION generateWebIds() RETURNS void AS $$

DECLARE
	ttcDoc varchar(36);
	versions varchar(36)[];
	is_version boolean := FALSE;
	web_id varchar;
	processed_doc integer := 0;
	total_doc integer;
BEGIN
	RAISE NOTICE 'Starting webId generation';

	-- le total de documents à traiter
	SELECT count(id) INTO total_doc FROM toutatice;

	-- pour tout les docs toutatice
	FOR ttcDoc IN SELECT id FROM toutatice LOOP

		-- on ignore les versions
		SELECT isversion INTO is_version FROM hierarchy WHERE id = ttcDoc;
		IF is_version IS NOT TRUE THEN
			-- on récupère toute les versions du live
			versions := array(SELECT id FROM versions WHERE versionableid = ttcDoc);
			
			-- si le live a un webid on le stocke
			SELECT webid INTO web_id FROM toutatice WHERE id = ttcDoc;
			
			IF (web_id IS NULL OR web_id LIKE '') THEN
    			-- sinon on génère le webid
    			SELECT array_to_string(array((SELECT SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz123456789' FROM trunc(random() * 63)::int FOR 1) INTO web_id FROM generate_series(1,6))),''); 
            END IF;

			-- maj le ttcDoc et ses version avec le webid du live ou généré
			UPDATE toutatice SET webid = web_id WHERE id = ANY (versions || ttcDoc) AND (webid != web_id OR webid IS NULL);
			
		END IF;

		processed_doc := processed_doc + 1;
		IF processed_doc % 1000 = 0 THEN
			RAISE INFO '% out of % documents processed', processed_doc, total_doc;
		END IF;
	END LOOP;

	RAISE NOTICE 'webId generation DONE, % documents processed ', processed_doc;

END ; $$ LANGUAGE plpgsql;
