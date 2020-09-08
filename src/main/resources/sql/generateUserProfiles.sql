CREATE OR REPLACE FUNCTION generateUserProfiles() RETURNS void AS $$

DECLARE
	processed_doc integer := 0;
BEGIN
	RAISE NOTICE 'Starting bio generation';
	
    UPDATE ttc_userprofile SET bio = u.bio
    FROM userprofile u INNER JOIN ttc_userprofile t ON
        u.id = t.id;
        
    GET DIAGNOSTICS processed_doc = ROW_COUNT;
    
	RAISE NOTICE 'update ttc_userprofiles DONE, % documents processed ', processed_doc;

    INSERT INTO ttc_userprofile(id, bio)
    SELECT u.id, u.bio FROM userprofile u
        WHERE u.id NOT IN (SELECT id FROM ttc_userprofile);

    GET DIAGNOSTICS processed_doc = ROW_COUNT;
    
	RAISE NOTICE 'insert into ttc_userprofiles DONE, % documents processed ', processed_doc;
	

END ; $$ LANGUAGE plpgsql;