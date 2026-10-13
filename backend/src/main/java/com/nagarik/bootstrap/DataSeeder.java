package com.nagarik.bootstrap;

import com.nagarik.domain.AppUser;
import com.nagarik.domain.Attachment;
import com.nagarik.domain.Category;
import com.nagarik.domain.Comment;
import com.nagarik.domain.Confirmation;
import com.nagarik.domain.Issue;
import com.nagarik.domain.IssueStatus;
import com.nagarik.domain.MediaKind;
import com.nagarik.domain.Role;
import com.nagarik.domain.Vote;
import com.nagarik.repo.AppUserRepository;
import com.nagarik.repo.AttachmentRepository;
import com.nagarik.repo.CommentRepository;
import com.nagarik.repo.ConfirmationRepository;
import com.nagarik.repo.IssueRepository;
import com.nagarik.repo.VoteRepository;
import com.nagarik.service.MediaStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills an empty database with a neighbourhood's worth of reports so the feed
 * has something to sort, filter, search and argue about on first run. Skipped
 * entirely once any report exists, so it never touches real data.
 *
 * Every seeded vote is a real row from a distinct resident - the counters are
 * not faked, which means toggling a vote in the UI produces an honest number.
 * The same goes for verification: a report that shows three residents saying it
 * was fixed has three confirmation rows behind it, from three of the people who
 * actually backed it, so the dashboard's tallies survive a recount.
 *
 * Reports are seeded across every stage of the lifecycle rather than only the
 * first one. A dashboard whose overdue column reads zero and whose verification
 * queue is empty demonstrates nothing, so there is deliberately one report past
 * its promised date, one waiting on backers, and one the neighbourhood rejected.
 *
 * Since accounts now carry passwords, this also creates the two logins worth
 * having on a fresh install: a moderator who can move reports along, and a
 * resident. Their credentials are printed to the console on first run, which is
 * the only place they are written down.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String[] FIRST_NAMES = {
            "Anita", "Ravi", "Sunita", "Prakash", "Meera", "Devendra",
            "Kavita", "Suresh", "Nisha", "Bikash", "Rekha", "Manoj",
            "Pooja", "Arun", "Lakshmi", "Gopal"
    };

    private static final String[] LAST_NAMES = {
            "Sharma", "Thapa", "Verma", "Gurung", "Patel", "Shrestha",
            "Joshi", "Rai", "Adhikari", "Bhandari", "Karki", "Magar"
    };

    /** The moderator account, so a fresh install has somebody who can resolve things. */
    private static final String MODERATOR_NAME = "Ward Moderator";
    private static final String MODERATOR_EMAIL = "moderator@nagarik.test";

    /**
     * One password for every seeded account, so the sample data is usable without
     * a lookup table. Not a secret - it only ever exists in a database that this
     * class created, and this class refuses to run on a database with reports in it.
     */
    private static final String DEMO_PASSWORD = "nagarik123";

    /** Must match IssueService, or seeded windows will not read as the app's own. */
    private static final int VERIFY_QUORUM_CAP = 5;
    private static final Duration VERIFY_WINDOW = Duration.ofDays(7);

    private final AppUserRepository users;
    private final IssueRepository issues;
    private final VoteRepository votes;
    private final CommentRepository comments;
    private final ConfirmationRepository confirmations;
    private final AttachmentRepository attachments;
    private final MediaStore media;
    private final PasswordEncoder encoder;

    public DataSeeder(AppUserRepository users, IssueRepository issues,
                      VoteRepository votes, CommentRepository comments,
                      ConfirmationRepository confirmations,
                      AttachmentRepository attachments,
                      MediaStore media,
                      PasswordEncoder encoder) {
        this.users = users;
        this.issues = issues;
        this.votes = votes;
        this.comments = comments;
        this.confirmations = confirmations;
        this.attachments = attachments;
        this.media = media;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (issues.count() > 0) {
            return;
        }

        // Hashing is deliberately done once and reused rather than once per
        // resident. BCrypt is built to be slow - 150 of them at strength 10 would
        // add roughly fifteen seconds to every first start, and these are throwaway
        // accounts. Real registrations go through UserService, where each one gets
        // its own salt from its own call.
        String sharedHash = encoder.encode(DEMO_PASSWORD);

        AppUser moderator = users.save(
                new AppUser(MODERATOR_NAME, MODERATOR_EMAIL, encoder.encode(DEMO_PASSWORD), Role.MODERATOR));

        // The busiest report is backed by 118 people and every backer must be a
        // distinct resident, so seed comfortably more than that.
        List<AppUser> residents = createResidents(150, sharedHash);

        // -- Nobody has picked these up yet. The moderator's inbox. ----------

        Issue sparks = seed(residents.get(3), Category.ELECTRICITY, 2,
                "Transformer by the water tank sparks every evening",
                "Sector 4, beside the vegetable market",
                "Around 7pm it starts crackling and throwing sparks onto the footpath below. "
                        + "Children walk under it coming back from tuition. Two shopkeepers have "
                        + "already called the line office and nobody has come to look at it.",
                residents, 47, 10,
                "It did the same thing last monsoon. They tightened something and left.",
                "I have the junior engineer's number from a previous complaint. Sharing it in the group.",
                "Please put a barricade under it at least until someone comes. Somebody is going to get hurt.");

        Issue pothole = seed(residents.get(27), Category.ROADS, 5,
                "Pothole at the school crossing is deep enough to throw a scooter",
                "Station Road, outside the girls' school gate",
                "It has been growing since the rain and is now about a foot deep, right where "
                        + "everyone slows down to let children cross. A delivery rider went down on "
                        + "Tuesday morning. It fills with water so you cannot judge the depth.",
                residents, 86, 5,
                "Someone has put a branch in it as a warning. That is all the warning there is.",
                "Photos from this morning show it has widened again after last night's rain.",
                "The school has written to the ward twice. Happy to add our names to a third letter.");

        Issue brown = seed(residents.get(55), Category.WATER, 3,
                "Supply runs brown for the first ten minutes every morning",
                "Ward 12, Ganga Nagar",
                "It clears if you let it run, but nobody should have to pour away twenty litres "
                        + "before they can cook. Started around the same time as the pump work.",
                residents, 52, 61,
                "Ours runs brown too. We have been filtering it but the filter clogs in days.",
                "Worth asking whether the tank was cleaned after the pump was opened up.");

        seed(residents.get(112), Category.OTHER, 4,
                "Stray dog pack has settled behind the market",
                "Sector 4, beside the vegetable market",
                "Eight or nine dogs, and two have started following children. Nobody wants them "
                        + "hurt - we want them collected, vaccinated and rehomed properly.",
                residents, 27, 90,
                "Please let us keep this humane. There is a rescue in the next ward that does catch and release.",
                "I have their number, will call on Monday and report back.");

        seed(residents.get(120), Category.ROADS, 1,
                "Speed breaker worn flat outside the primary school",
                "Hospital Road, near the diagnostic centre",
                "It was repainted in the spring but the hump itself has worn down to almost "
                        + "nothing. Traffic comes past the school gate at the same speed as the main road.",
                residents, 12, 44,
                "Paint is not a speed breaker. It needs rebuilding, not remarking.");

        // -- Seen, dated, not started. ---------------------------------------

        Issue slabs = seed(residents.get(63), Category.ROADS, 30,
                "Footpath slabs lifted outside the clinic",
                "Hospital Road, near the diagnostic centre",
                "Three slabs have risen about four inches where a root has pushed under them. "
                        + "People coming out of the clinic with sticks and frames have to step into "
                        + "the road to get past.",
                residents, 71, 12,
                "My mother fell here last month. We did not report it, which I now regret.",
                "Ward engineer came and measured on Thursday, so something is moving.");
        acknowledge(slabs, moderator, 3, 9,
                "Measured on site. Slabs are being cut to size at the yard.");

        // The overdue one. Highest-backed report on the board, promised for a
        // date that has now passed - which is exactly the row the dashboard's
        // overdue counter exists to surface.
        Issue manhole = seed(residents.get(94), Category.DRAINAGE, 25,
                "Manhole cover missing at the junction",
                "Station Road, at the Lane 3 junction",
                "The cover has been gone for over a month. Someone has stood a bamboo pole in it "
                        + "but at night you cannot see the pole either. This is on the route children "
                        + "take to school.",
                residents, 103, 88,
                "This is the most dangerous thing on this list. Please do not let it drop.",
                "Ward office says a replacement cover is ordered. Asked them to barricade it meanwhile.",
                "Barricade appeared yesterday. Still no cover.");
        acknowledge(manhole, moderator, 12, -5,
                "Replacement cover ordered from the depot. Barricade in place meanwhile.");

        // -- Crew is out. ----------------------------------------------------

        Issue supply = seed(residents.get(11), Category.WATER, 9,
                "No morning supply in Ward 12 for nine days now",
                "Ward 12, Ganga Nagar",
                "The morning line has been dry since the 1st. Tankers come once every three days "
                        + "and there is a scramble every time. Households at the end of the lane get "
                        + "nothing at all. The board says a pump is being repaired but there is no date.",
                residents, 118, 22,
                "Confirmed at the ward office today - pump motor burnt out, replacement is on order.",
                "Nine days without a date is not a repair, it is a shrug. Can we ask for tanker timings in writing?",
                "Tanker came at 6am today without notice and left before half the lane knew.",
                "If someone can share the ward office email I will write asking for a published schedule.");
        startWork(supply, moderator, 6, 4,
                "Replacement motor fitted, testing the line. Tankers continue twice daily until then.");

        Issue lights = seed(residents.get(19), Category.STREETLIGHT, 7,
                "Six street lights dark on the stretch to the highway",
                "Bypass Road, between the petrol pump and the flyover",
                "The entire stretch past the petrol pump is unlit. Women walking back from the "
                        + "evening shift at the garment unit have started taking the longer route "
                        + "through the colony instead because this one feels unsafe.",
                residents, 94, 52,
                "Ward office has logged it and says the contractor will attend this week. Holding them to that.",
                "Two of the six came on last night. Four still dark.");
        startWork(lights, moderator, 4, 2,
                "Contractor on site. Two fittings replaced, four to go.");

        // -- Marked done, waiting on the people who raised it. ---------------

        Issue drain = seed(residents.get(41), Category.DRAINAGE, 14,
                "Drain overflows across the footpath after every shower",
                "Lane 3, Shanti Colony",
                "The drain at the corner backs up within minutes of rain starting and spreads "
                        + "across the whole footpath, so people walk in the road instead. It has not "
                        + "been cleared since before the last festival.",
                residents, 34, 33,
                "Same at the other end of the lane. Whatever is blocking it is between the two corners.",
                "Corner drain runs clear now. Waiting on the next shower to be sure.",
                "Mine is the no vote - the far end still pools even though the corner is fine.");
        openVerification(drain, moderator, 9, 2, residents, 3, 1,
                "Both corners rodded and the silt trap emptied.");

        // Nobody has answered this one yet, which is the state the resident
        // board has to render before anybody has pressed anything.
        Issue flicker = seed(residents.get(101), Category.STREETLIGHT, 6,
                "The light at the bus stop flickers all night",
                "Ward 9, behind the bus depot",
                "It never fully fails so it never gets reported, but it strobes from dusk to dawn. "
                        + "The families in the two houses opposite have been sleeping with the "
                        + "curtains taped shut.",
                residents, 18, 35,
                "A flickering light is still counted as a working light, which is why this never gets fixed.");
        openVerification(flicker, moderator, 4, 1, residents, 0, 0,
                "Choke replaced on the pole. Please confirm once it has been through a full night.");

        // -- The neighbourhood said it did not hold. -------------------------

        Issue bins = seed(residents.get(87), Category.GARBAGE, 11,
                "Nobody has emptied the bins at the park gate since the festival",
                "Shanti Colony park, main gate",
                "Both bins have been overflowing for a fortnight and the overflow has spread into "
                        + "the hedge. It smells from across the road and the morning walkers have "
                        + "started going elsewhere.",
                residents, 41, 70,
                "I called the sanitation number twice. Both times they said the park is not on their list.",
                "If the park is not on anyone's list then who put the bins there?",
                "They emptied the bins and left everything that had spilled into the hedge. Voting no.");
        dispute(bins, moderator, 9, -3, 4, 1, residents,
                "Bins emptied and the park added to the Tuesday round.");

        // -- Settled, with the backing to show for it. -----------------------

        Issue collection = seed(residents.get(8), Category.GARBAGE, 21,
                "Collection van stopped coming down our lane",
                "Ward 9, behind the bus depot",
                "The van used to come on alternate mornings and stopped about three weeks ago. "
                        + "Waste is piling up at the lane mouth and dogs pull it apart overnight.",
                residents, 29, 45,
                "The van driver told me the lane was dropped from the route by mistake when the schedule changed.",
                "Collection restarted on Monday. Marking this resolved - thanks to everyone who called.");
        resolve(collection, moderator, 14, 13, 6, 5, 0, residents,
                "Lane restored to the alternate-morning route.");

        Issue cuts = seed(residents.get(72), Category.ELECTRICITY, 40,
                "Power cuts every afternoon between two and four",
                "Sector 4, beside the vegetable market",
                "Daily, almost to the minute. The shops with cold storage are losing stock and "
                        + "nobody has told us whether it is load shedding or a fault.",
                residents, 63, 28,
                "It was a fault on the feeder, fixed on the 14th. Two weeks of clear afternoons since.");
        resolve(cuts, moderator, 30, 19, 12, 4, 1, residents,
                "Faulty section of the feeder replaced.");

        // -- Sample photos. ---------------------------------------------------
        //
        // Last, deliberately: the stage helpers above save the issue several
        // times each, and attaching a photo writes the attachment count onto the
        // same row. Doing this at the end means nothing overwrites it afterwards.
        //
        // Who uploaded what is varied on purpose. Most are from the resident who
        // raised the report, two are from the moderator documenting the work, and
        // one is from a different resident entirely - which is the case the
        // permission rule exists for: anyone signed in may add evidence, only the
        // uploader or a moderator may take it down.
        photo(pothole, pothole.getAuthor(), "Pothole at the school crossing");
        photo(pothole, residents.get(31), "Same pothole after Tuesday's rain");
        photo(sparks, sparks.getAuthor(), "Transformer by the water tank");
        photo(brown, brown.getAuthor(), "First water of the morning");
        photo(manhole, manhole.getAuthor(), "Open manhole at the junction");
        photo(manhole, moderator, "Barricade placed by the ward");
        photo(lights, moderator, "Contractor replacing the fittings");
        photo(bins, bins.getAuthor(), "Bins at the park gate");
        photo(drain, drain.getAuthor(), "Drain overflowing across the footpath");

        log.info("Seeded {} reports and {} residents.", issues.count(), users.count());
        log.info("Seeded {} sample photos into {}", attachments.count(), media.getRoot());
        log.info("Demo logins - moderator: {} / {}, resident: {} / {}",
                MODERATOR_EMAIL, DEMO_PASSWORD,
                residents.get(0).getEmail(), DEMO_PASSWORD);
    }

    private List<AppUser> createResidents(int howMany, String passwordHash) {
        // Names are one first name paired with one last name, so the supply of
        // distinct residents is bounded. Past the ceiling the pairs repeat and
        // the unique constraint on display_name rejects them - fail loudly here
        // rather than halfway through seeding.
        int ceiling = FIRST_NAMES.length * LAST_NAMES.length;
        if (howMany > ceiling) {
            throw new IllegalArgumentException(
                    "Only " + ceiling + " distinct seed names exist, asked for " + howMany);
        }

        List<AppUser> created = new ArrayList<>();
        for (int i = 0; i < howMany; i++) {
            String first = FIRST_NAMES[i % FIRST_NAMES.length];
            String last = LAST_NAMES[(i / FIRST_NAMES.length) % LAST_NAMES.length];
            String displayName = first + " " + last;
            // The .test top level domain is reserved by RFC 2606, so these addresses
            // can never accidentally reach a real inbox. They are also lower-cased
            // because that is what registration stores and looks up.
            String email = (first + "." + last).toLowerCase() + "@nagarik.test";
            created.add(users.save(new AppUser(displayName, email, passwordHash, Role.RESIDENT)));
        }
        return created;
    }

    // ---- Sample photos ----------------------------------------------------

    /** Matches the category colours in styles.css, so a seeded photo looks like
     *  it belongs to the report it is on rather than to a stock library. */
    private static Color tintFor(Category category) {
        return switch (category) {
            case ELECTRICITY -> new Color(0xD1, 0x60, 0x3D);
            case WATER -> new Color(0x2B, 0x7F, 0xB8);
            case ROADS -> new Color(0x7A, 0x66, 0x98);
            case DRAINAGE -> new Color(0x3F, 0x8F, 0x7B);
            case GARBAGE -> new Color(0x8A, 0x7A, 0x35);
            case STREETLIGHT -> new Color(0xC0, 0x8A, 0x2E);
            case OTHER -> new Color(0x6B, 0x7F, 0x85);
        };
    }

    /**
     * Draws a stand-in photo and attaches it to a report.
     *
     * Generated rather than shipped as files for two reasons: a repository for a
     * college project should not carry a megabyte of stock JPEGs, and a seeded
     * image that is obviously a placeholder is honest - nobody demonstrating
     * this can mistake it for a real photograph of a real pothole, which they
     * could if it were a stock photo.
     *
     * It goes through MediaStore rather than writing to disk directly, so the
     * seeded rows are indistinguishable from uploaded ones: same naming, same
     * directory, same sniffing. If the write fails the report is still fine, so
     * this logs and moves on rather than aborting a seed that is otherwise done.
     */
    private void photo(Issue issue, AppUser uploader, String caption) {
        try {
            byte[] bytes = drawSample(issue.getCategory(), caption);

            MediaStore.Sniffed sniffed = media.sniff(bytes);
            if (sniffed == null) {
                // Would mean ImageIO produced something that is not a PNG,
                // which cannot happen - but silently storing an unreadable
                // attachment would be worse than noticing.
                log.warn("Skipped a sample photo: the generated bytes did not sniff as an image.");
                return;
            }

            int[] size = media.imageDimensions(bytes);
            String storedName = media.write(bytes, sniffed);

            attachments.save(new Attachment(
                    issue,
                    uploader,
                    MediaKind.IMAGE,
                    sniffed.contentType(),
                    bytes.length,
                    storedName,
                    caption.toLowerCase().replace(' ', '-') + ".png",
                    size == null ? null : size[0],
                    size == null ? null : size[1]));

            issue.setAttachmentCount((int) attachments.countByIssueId(issue.getId()));
            issues.save(issue);
        } catch (RuntimeException e) {
            log.warn("Could not attach a sample photo to \"{}\": {}", issue.getTitle(), e.getMessage());
        }
    }

    /**
     * A 1200x800 PNG: the category colour, a diagonal band to stop it reading as
     * a flat swatch, the caption, and a line making clear it is sample data.
     *
     * Headless-safe. Spring Boot sets java.awt.headless=true, which BufferedImage
     * and ImageIO are fine with - they need no display, only the rasteriser.
     */
    private byte[] drawSample(Category category, String caption) {
        int width = 1200;
        int height = 800;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Color tint = tintFor(category);
        Color deep = tint.darker().darker();

        g.setColor(deep);
        g.fillRect(0, 0, width, height);

        // A wide diagonal in the category colour. Enough shape that a gallery of
        // six of these does not look like six identical rectangles.
        g.setColor(tint);
        g.fillPolygon(
                new int[] { 0, width, width, 0 },
                new int[] { height - 220, height - 480, height, height },
                4);

        g.setColor(new Color(255, 255, 255, 28));
        g.fillOval(width - 360, -140, 520, 520);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 54));
        g.drawString(category.getLabel(), 72, 140);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 38));
        int y = 230;
        for (String line : wrap(caption, 36)) {
            g.drawString(line, 72, y);
            y += 54;
        }

        g.setColor(new Color(255, 255, 255, 190));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
        g.drawString("Sample image generated by the seeder — not a real photograph", 72, height - 56);

        g.dispose();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode a sample photo", e);
        }
    }

    /** Greedy wrap, so a long caption does not run off the canvas. */
    private List<String> wrap(String text, int perLine) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > perLine) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /**
     * One report, its backing and its discussion. Always lands as Reported;     * anything further along is applied afterwards by the stage helpers, so the
     * seeded history reads in the same order it would have happened in.
     *
     * @param daysAgo      how far back to date it, so "newest" has something to sort
     * @param backing      how many residents back it; taken from the front of the list
     * @param commentStart where in the resident list the repliers come from, so the
     *                     same handful of names do not appear under every report
     */
    private Issue seed(AppUser author, Category category, int daysAgo,
                       String title, String area, String description,
                       List<AppUser> residents, int backing, int commentStart, String... bodies) {

        Issue issue = new Issue(title, description, category, area, author);
        issue.setCreatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        issues.save(issue);

        int backers = Math.min(backing, residents.size());
        for (int i = 0; i < backers; i++) {
            votes.save(new Vote(issue, residents.get(i)));
        }
        issue.setVoteCount(backers);

        for (int i = 0; i < bodies.length; i++) {
            AppUser replier = residents.get((commentStart + i) % residents.size());
            Comment comment = new Comment(issue, replier, bodies[i]);
            // Space the replies out after the report rather than stamping them
            // all at "now", so the discussion reads as a conversation.
            comment.setCreatedAt(issue.getCreatedAt().plus(i + 1L, ChronoUnit.HOURS));
            comments.save(comment);
        }
        issue.setCommentCount(bodies.length);

        return issues.save(issue);
    }

    /**
     * A moderator picking it up and committing to a date.
     *
     * @param etaInDays days from today; negative puts the promise in the past,
     *                  which is how a report becomes overdue
     */
    private void acknowledge(Issue issue, AppUser moderator, int ackDaysAgo, int etaInDays, String note) {
        issue.setStatus(IssueStatus.ACKNOWLEDGED);
        issue.setAcknowledgedAt(Instant.now().minus(ackDaysAgo, ChronoUnit.DAYS));
        issue.setAcknowledgedBy(moderator);
        issue.setEtaDate(LocalDate.now().plusDays(etaInDays));
        issue.setWorkNote(note);
        issues.save(issue);
    }

    private void startWork(Issue issue, AppUser moderator, int ackDaysAgo, int etaInDays, String note) {
        acknowledge(issue, moderator, ackDaysAgo, etaInDays, note);
        issue.setStatus(IssueStatus.IN_PROGRESS);
        issues.save(issue);
    }

    /**
     * Marked done, with the window still open.
     *
     * The confirmation rows come from the front of the resident list because that
     * is where the backers came from - the app only lets backers answer, so
     * seeding an answer from anyone else would produce a tally the running
     * application could never have produced.
     */
    private void openVerification(Issue issue, AppUser moderator, int ackDaysAgo, int openedDaysAgo,
                                  List<AppUser> residents, int saidFixed, int saidNot, String note) {
        acknowledge(issue, moderator, ackDaysAgo, 0, note);

        Instant opened = Instant.now().minus(openedDaysAgo, ChronoUnit.DAYS);
        issue.setStatus(IssueStatus.VERIFYING);
        issue.setEtaDate(null);
        issue.setVerifyOpenedAt(opened);
        issue.setVerifyDeadline(opened.plus(VERIFY_WINDOW));
        issue.setVerifyQuorum(Math.min(VERIFY_QUORUM_CAP, issue.getVoteCount()));
        recordAnswers(issue, residents, saidFixed, saidNot);
        issues.save(issue);
    }

    /** Enough backers said yes. Fixed, and on the record that it was checked. */
    private void resolve(Issue issue, AppUser moderator, int ackDaysAgo, int openedDaysAgo,
                         int resolvedDaysAgo, int saidFixed, int saidNot,
                         List<AppUser> residents, String note) {
        openVerification(issue, moderator, ackDaysAgo, openedDaysAgo, residents, saidFixed, saidNot, note);
        issue.setStatus(IssueStatus.RESOLVED);
        issue.setResolvedAt(Instant.now().minus(resolvedDaysAgo, ChronoUnit.DAYS));
        issues.save(issue);
    }

    /**
     * The neighbourhood said the repair did not hold, so it is back on the pile
     * with its promised date already behind it.
     *
     * The window is left open-ended rather than expired, because a report that
     * reaches quorum against settles the moment the last vote lands - the
     * deadline it never reached stays in the future, and the sweep leaves it
     * alone because it is no longer in Verifying.
     */
    private void dispute(Issue issue, AppUser moderator, int ackDaysAgo, int etaInDays,
                         int saidNot, int saidFixed, List<AppUser> residents, String note) {
        openVerification(issue, moderator, ackDaysAgo, 4, residents, saidFixed, saidNot, note);
        issue.setStatus(IssueStatus.DISPUTED);
        issue.setEtaDate(LocalDate.now().plusDays(etaInDays));
        issues.save(issue);
    }

    /** Real confirmation rows, so the two counters are a count of something. */
    private void recordAnswers(Issue issue, List<AppUser> residents, int saidFixed, int saidNot) {
        int backers = issue.getVoteCount();
        int yes = Math.min(saidFixed, backers);
        int no = Math.min(saidNot, Math.max(backers - yes, 0));

        for (int i = 0; i < yes; i++) {
            confirmations.save(new Confirmation(issue, residents.get(i), true));
        }
        for (int i = yes; i < yes + no; i++) {
            confirmations.save(new Confirmation(issue, residents.get(i), false));
        }

        issue.setConfirmedCount(yes);
        issue.setDeniedCount(no);
    }
}
